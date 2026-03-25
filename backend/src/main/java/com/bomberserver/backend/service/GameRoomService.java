package com.bomberserver.backend.service;

import com.bomberserver.backend.config.GameConfigProperties;
import com.bomberserver.backend.document.CharacterProfileDocument;
import com.bomberserver.backend.document.MatchHistoryDocument;
import com.bomberserver.backend.dto.ClientMessage;
import com.bomberserver.backend.dto.ServerMessage;
import com.bomberserver.backend.model.*;
import com.bomberserver.backend.repository.CharacterProfileRepository;
import com.bomberserver.backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameRoomService {

    // =========================================================
    // QUICK_PLAY:
    // - chế độ chơi nhanh
    // - không đi qua private room lobby
    // =========================================================
    private static final String QUICK_PLAY_KEY = "QUICK-PLAY";

    // =========================================================
    // MATCH MODE:
    // - SOLO = chơi đơn
    // - DUO  = chơi đôi 2v2
    // =========================================================
    private static final String MATCH_MODE_SOLO = "SOLO";
    private static final String MATCH_MODE_DUO = "DUO";

    // =========================================================
    // SPEED_SKILL_DURATION_MS:
    // - thời gian tồn tại của skill tăng tốc
    // =========================================================
    private static final long SPEED_SKILL_DURATION_MS = 5000L;

    private final ObjectMapper objectMapper;
    private final GameConfigProperties gameConfig;
    private final Random random = new Random();
    private final JwtService jwtService;
    private final CharacterProfileRepository characterProfileRepository;
    private final HistoryService historyService;
    private final BotService botService;

    // =========================================================
    // roomLobbyService:
    // - dùng để xóa room khỏi lobby khi trận kết thúc
    // - hoặc khi tất cả người chơi đã rời trận
    // =========================================================
    private final RoomLobbyService roomLobbyService;

    // =========================================================
    // matches:
    // - key   = matchKey / roomCode
    // - value = trạng thái nội bộ của 1 trận
    // =========================================================
    private final Map<String, MatchInstance> matches = new ConcurrentHashMap<>();

    public GameRoomService(
            ObjectMapper objectMapper,
            GameConfigProperties gameConfig,
            JwtService jwtService,
            CharacterProfileRepository characterProfileRepository,
            HistoryService historyService,
            BotService botService,
            RoomLobbyService roomLobbyService
    ) {
        this.objectMapper = objectMapper;
        this.gameConfig = gameConfig;
        this.jwtService = jwtService;
        this.characterProfileRepository = characterProfileRepository;
        this.historyService = historyService;
        this.botService = botService;
        this.roomLobbyService = roomLobbyService;
    }

    // =========================================================
    // HÀM: join
    // Mục đích:
    // - Cho người chơi vào trận
    //
    // Nguồn dữ liệu:
    // - roomCode
    // - requiredPlayers
    // - humanCount
    // - botCount
    // - matchMode / queueMode
    //
    // Nếu là quick play:
    // - tách queue riêng SOLO / DUO
    //
    // Nếu là custom room:
    // - giữ đúng config người thật / bot từ lobby truyền sang
    // =========================================================
    public synchronized Integer join(WebSocketSession session) {
        String matchKey = extractRoomCodeFromSession(session);
        int requiredPlayers = extractRequiredPlayersFromSession(session);
        int expectedHumanCount = extractHumanCountFromSession(session);
        int expectedBotCount = extractBotCountFromSession(session);
        String matchMode = extractMatchModeFromSession(session);

        // Nếu không có roomCode thì đây là quick play.
        // Tách queue SOLO / DUO để không bị trộn.
        if (matchKey == null || matchKey.isBlank()) {
            matchKey = buildQuickPlayMatchKey(matchMode);
        }

        // Quick play không dùng bot lobby
        if (matchKey.startsWith(QUICK_PLAY_KEY)) {
            requiredPlayers = gameConfig.getMatch().getDefaultRequiredPlayers();
            expectedHumanCount = requiredPlayers;
            expectedBotCount = 0;
        }

        if (requiredPlayers < gameConfig.getMatch().getMinRequiredPlayers()
                || requiredPlayers > gameConfig.getMatch().getMaxRequiredPlayers()) {
            requiredPlayers = gameConfig.getMatch().getDefaultRequiredPlayers();
        }

        if (expectedBotCount < 0) {
            expectedBotCount = 0;
        }
        if (expectedBotCount > requiredPlayers) {
            expectedBotCount = requiredPlayers;
        }

        // Nếu lobby không truyền humanCount thì tự suy ra
        if (expectedHumanCount <= 0) {
            expectedHumanCount = requiredPlayers - expectedBotCount;
        }

        if (expectedHumanCount < 1) {
            expectedHumanCount = 1;
        }
        if (expectedHumanCount > requiredPlayers) {
            expectedHumanCount = requiredPlayers;
        }

        if (expectedHumanCount + expectedBotCount > requiredPlayers) {
            expectedBotCount = Math.max(0, requiredPlayers - expectedHumanCount);
        }

        final String finalMatchKey = matchKey;
        final int finalRequiredPlayers = requiredPlayers;
        final int finalExpectedHumanCount = expectedHumanCount;
        final int finalExpectedBotCount = expectedBotCount;
        final String finalMatchMode = sanitizeMatchMode(matchMode);

        MatchInstance match = matches.computeIfAbsent(
                finalMatchKey,
                key -> createMatch(
                        key,
                        finalRequiredPlayers,
                        finalExpectedHumanCount,
                        finalExpectedBotCount,
                        finalMatchMode
                )
        );

        // Nếu join sau thì vẫn cập nhật config từ lobby / quick play
        match.expectedHumanCount = finalExpectedHumanCount;
        match.expectedBotCount = finalExpectedBotCount;
        match.matchMode = finalMatchMode;

        for (int id = 1; id <= match.requiredPlayers; id++) {
            if (!isOccupiedSlot(match, id)) {
                Player fresh = createFreshPlayer(id);

                // Gán team theo mode
                applyMatchModeToPlayer(match, fresh);

                match.players.put(id, fresh);

                match.sessions.put(id, session);
                session.getAttributes().put("playerId", id);
                session.getAttributes().put("matchKey", finalMatchKey);

                // Người vào đầu tiên trong custom room là host trận
                if (match.customRoom && match.hostPlayerId == null) {
                    match.hostPlayerId = id;
                }

                attachProfileToPlayer(match, session, id);

                // Khi đủ người thật rồi thì sync bot vào slot còn thiếu
                maybeSyncConfiguredBots(match);

                reevaluateWaitingState(match);
                return id;
            }
        }

        return null;
    }

    // =========================================================
    // HÀM: leave
    // Mục đích:
    // - Xử lý người chơi rời khỏi trận
    //
    // Điểm sửa quan trọng:
    // - Nếu tất cả session người thật đều đã rời trận
    //   => xóa match
    //   => đồng thời xóa room khỏi lobby nếu là custom room
    // =========================================================
    public synchronized void leave(String matchKey, int playerId) {
        MatchInstance match = matches.get(matchKey);
        if (match == null) return;

        match.sessions.remove(playerId);

        Player leaving = match.players.get(playerId);
        if (leaving != null) {
            leaving.lives = 0;
            leaving.row = -99;
            leaving.col = -99;
            updateOvr(leaving);
        }

        // Nếu không còn session người thật nào nữa
        // thì dọn cả match + dọn room lobby nếu có
        if (match.sessions.isEmpty()) {
            finishLobbyRoomIfNeeded(match);
            matches.remove(matchKey);
            return;
        }

        // Nếu host của trận rời thì chọn host người thật tiếp theo
        if (Objects.equals(match.hostPlayerId, playerId)) {
            match.hostPlayerId = findNextHumanHost(match);
        }

        // Nếu người thật rời trước lúc bắt đầu thì sync lại bot
        maybeSyncConfiguredBots(match);

        reevaluateWaitingState(match);

        if (match.gameStarted) {
            checkWinner(match);
        }

        broadcastState(matchKey);
    }

    // =========================================================
    // HÀM: sendInit
    // Mục đích:
    // - Gửi playerId ban đầu về cho client
    // =========================================================
    public synchronized void sendInit(WebSocketSession session, int playerId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("playerId", playerId);
        send(session, new ServerMessage("init", payload));
    }

    // =========================================================
    // HÀM: handleClientMessage
    // Mục đích:
    // - Nhận message điều khiển từ client trong trận
    // - move / bomb / use_item / skill / add_bot / restart
    // =========================================================
    public synchronized void handleClientMessage(String matchKey, int playerId, ClientMessage message) {
        MatchInstance match = matches.get(matchKey);
        if (match == null || message == null || message.type == null) return;

        switch (message.type) {
            case "move" -> {
                if (!match.gameOver && match.gameStarted) {
                    handleMove(match, playerId, message.direction);
                }
            }

            case "bomb" -> {
                if (!match.gameOver && match.gameStarted) {
                    handlePlaceBomb(match, playerId);
                }
            }

            case "use_item" -> {
                if (!match.gameOver && match.gameStarted) {
                    handleUseItem(match, playerId, message.slotIndex);
                }
            }

            case "skill_bomb" -> {
                if (!match.gameOver && match.gameStarted) {
                    handleUseBombSkill(match, playerId);
                }
            }

            case "skill_speed" -> {
                if (!match.gameOver && match.gameStarted) {
                    handleUseSpeedSkill(match, playerId);
                }
            }

            // Thêm bot ở màn chờ game
            case "add_bot" -> {
                if (!match.gameOver && !match.gameStarted) {
                    handleAddBot(match, playerId);
                }
            }

            case "restart" -> handleRestart(match, playerId);

            default -> {
                // Không làm gì với type không hỗ trợ
            }
        }

        broadcastState(matchKey);
    }

    // =========================================================
    // HÀM: createMatch
    // Mục đích:
    // - Tạo trận mới
    // - Gắn config room / số người / số bot / kiểu trận
    // =========================================================
    private MatchInstance createMatch(
            String matchKey,
            int requiredPlayers,
            int expectedHumanCount,
            int expectedBotCount,
            String matchMode
    ) {
        MatchInstance match = new MatchInstance();
        match.matchKey = matchKey;
        match.requiredPlayers = requiredPlayers;
        match.expectedHumanCount = expectedHumanCount;
        match.expectedBotCount = expectedBotCount;
        match.matchMode = sanitizeMatchMode(matchMode);

        // QUICK-PLAY-SOLO / QUICK-PLAY-DUO đều là quick play
        match.customRoom = !matchKey.startsWith(QUICK_PLAY_KEY);
        match.hostPlayerId = null;

        resetMatch(match);
        return match;
    }

    // =========================================================
    // HÀM: sanitizeMatchMode
    // Mục đích:
    // - Chuẩn hóa kiểu trận
    // - Chỉ cho phép SOLO hoặc DUO
    // =========================================================
    private String sanitizeMatchMode(String raw) {
        if (raw != null && MATCH_MODE_DUO.equalsIgnoreCase(raw.trim())) {
            return MATCH_MODE_DUO;
        }
        return MATCH_MODE_SOLO;
    }

    // =========================================================
    // HÀM: buildQuickPlayMatchKey
    // Mục đích:
    // - Tách queue quick play theo từng kiểu trận
    // - SOLO và DUO không bị ghép chung với nhau
    // =========================================================
    private String buildQuickPlayMatchKey(String matchMode) {
        return QUICK_PLAY_KEY + "-" + sanitizeMatchMode(matchMode);
    }

    // =========================================================
    // HÀM: extractMatchModeFromSession
    // Mục đích:
    // - Lấy kiểu trận từ query param
    //
    // Ưu tiên:
    // - queueMode: quick play
    // - matchMode: room riêng
    // - mode: fallback cũ nếu ai đó truyền thẳng SOLO / DUO
    // =========================================================
    private String extractMatchModeFromSession(WebSocketSession session) {
        String value = extractQueryParam(session, "queueMode");

        if (value == null || value.isBlank()) {
            value = extractQueryParam(session, "matchMode");
        }

        if (value == null || value.isBlank()) {
            String legacyMode = extractQueryParam(session, "mode");
            if (MATCH_MODE_SOLO.equalsIgnoreCase(legacyMode)
                    || MATCH_MODE_DUO.equalsIgnoreCase(legacyMode)) {
                value = legacyMode;
            }
        }

        return sanitizeMatchMode(value);
    }

    // =========================================================
    // HÀM: applyMatchModeToPlayer
    // Mục đích:
    // - Gán teamId cho player theo kiểu trận hiện tại
    //
    // Quy ước DUO:
    // - Player 1 + Player 3 = LEFT
    // - Player 2 + Player 4 = RIGHT
    // =========================================================
    private void applyMatchModeToPlayer(MatchInstance match, Player player) {
        if (player == null) return;

        if (MATCH_MODE_DUO.equals(match.matchMode)) {
            player.teamId = (player.id == 1 || player.id == 3) ? "LEFT" : "RIGHT";
            return;
        }

        // SOLO: mỗi người là 1 team riêng
        player.teamId = "P" + player.id;
    }

    // =========================================================
    // HÀM: isSameTeam
    // Mục đích:
    // - Kiểm tra 2 player có cùng team không
    // =========================================================
    private boolean isSameTeam(Player first, Player second) {
        if (first == null || second == null) return false;
        if (first.teamId == null || second.teamId == null) return false;
        return Objects.equals(first.teamId, second.teamId);
    }

    // =========================================================
    // HÀM: isBlockedFriendlyFire
    // Mục đích:
    // - Ở DUO: đồng đội không gây damage / freeze cho nhau
    // - nhưng bản thân vẫn có thể tự dính bom của mình
    // =========================================================
    private boolean isBlockedFriendlyFire(MatchInstance match, Player attacker, Player victim) {
        if (match == null || attacker == null || victim == null) return false;
        if (!MATCH_MODE_DUO.equals(match.matchMode)) return false;
        if (attacker.id == victim.id) return false;
        return isSameTeam(attacker, victim);
    }

    // =========================================================
    // HÀM: getTeamDisplayName
    // Mục đích:
    // - Đổi teamId sang text dễ đọc
    // =========================================================
    private String getTeamDisplayName(String teamId) {
        if ("LEFT".equals(teamId)) return "Đội Trái";
        if ("RIGHT".equals(teamId)) return "Đội Phải";
        return "Đội";
    }

    // =========================================================
    // HÀM: handleRestart
    // Mục đích:
    // - Reset lại trận
    // - Giữ lại config room lobby
    // - Sync bot theo cấu hình trước đó
    // =========================================================
    private void handleRestart(MatchInstance match, int playerId) {
        if (!match.sessions.containsKey(playerId)) return;

        resetMatch(match);
        maybeSyncConfiguredBots(match);
        reevaluateWaitingState(match);
    }

    // =========================================================
    // HÀM: handleAddBot
    // Mục đích:
    // - Host của trận custom room thêm bot trực tiếp ở màn chờ
    // =========================================================
    private void handleAddBot(MatchInstance match, int requesterPlayerId) {
        if (!match.customRoom) return;
        if (!Objects.equals(match.hostPlayerId, requesterPlayerId)) return;
        if (match.gameStarted) return;
        if (getParticipantCount(match) >= match.requiredPlayers) return;

        Integer freeSlot = findNextFreeSlot(match);
        if (freeSlot == null) return;

        Player bot = createFreshPlayer(freeSlot);

        // Gán team cho bot theo mode hiện tại
        applyMatchModeToPlayer(match, bot);

        bot.bot = true;
        bot.ready = true;
        bot.displayName = "BOT_" + freeSlot;
        bot.characterName = "BOT_" + freeSlot;
        bot.gender = "male";
        bot.avatarCode = "bot";
        match.players.put(freeSlot, bot);

        // Cập nhật expectedBotCount để restart không bị mất bot
        match.expectedBotCount = countCurrentBots(match);

        reevaluateWaitingState(match);
    }

    // =========================================================
    // HÀM: maybeSyncConfiguredBots
    // Mục đích:
    // - Đồng bộ bot theo cấu hình lobby
    //
    // Logic:
    // - Nếu chưa đủ người thật thì chưa cho bot vào
    // - Nếu dư bot thì xóa bớt
    // - Nếu thiếu bot thì thêm vào
    // =========================================================
    private boolean maybeSyncConfiguredBots(MatchInstance match) {
        if (!match.customRoom) {
            return false;
        }

        boolean changed = false;

        // Chưa đủ người thật -> gỡ bot ra để chờ người thật vào
        if (!match.gameStarted && match.sessions.size() < match.expectedHumanCount) {
            Iterator<Map.Entry<Integer, Player>> iterator = match.players.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, Player> entry = iterator.next();
                Player p = entry.getValue();
                if (p != null && p.bot) {
                    iterator.remove();
                    changed = true;
                }
            }
            return changed;
        }

        int currentBotCount = countCurrentBots(match);

        // Nếu dư bot so với cấu hình thì xóa bớt
        if (!match.gameStarted && currentBotCount > match.expectedBotCount) {
            List<Integer> botSlots = new ArrayList<>();

            for (Map.Entry<Integer, Player> entry : match.players.entrySet()) {
                Player p = entry.getValue();
                if (p != null && p.bot) {
                    botSlots.add(entry.getKey());
                }
            }

            botSlots.sort(Comparator.reverseOrder());

            int needRemove = currentBotCount - match.expectedBotCount;
            for (Integer slot : botSlots) {
                if (needRemove <= 0) break;
                match.players.remove(slot);
                needRemove--;
                changed = true;
            }

            currentBotCount = countCurrentBots(match);
        }

        // Nếu thiếu bot thì thêm đúng số cần
        while (currentBotCount < match.expectedBotCount) {
            Integer freeSlot = findNextFreeSlot(match);
            if (freeSlot == null) break;

            Player bot = createFreshPlayer(freeSlot);

            // Gán team cho bot theo mode hiện tại
            applyMatchModeToPlayer(match, bot);

            bot.bot = true;
            bot.ready = true;
            bot.displayName = "BOT_" + freeSlot;
            bot.characterName = "BOT_" + freeSlot;
            bot.gender = "male";
            bot.avatarCode = "bot";

            match.players.put(freeSlot, bot);
            currentBotCount++;
            changed = true;
        }

        return changed;
    }

    // =========================================================
    // HÀM: findNextFreeSlot
    // Mục đích:
    // - Tìm slot player còn trống
    // =========================================================
    private Integer findNextFreeSlot(MatchInstance match) {
        for (int id = 1; id <= match.requiredPlayers; id++) {
            if (!isOccupiedSlot(match, id)) {
                return id;
            }
        }
        return null;
    }

    // =========================================================
    // HÀM: countCurrentBots
    // Mục đích:
    // - Đếm số bot hiện có trong trận
    // =========================================================
    private int countCurrentBots(MatchInstance match) {
        int count = 0;
        for (Player p : match.players.values()) {
            if (p != null && p.bot) {
                count++;
            }
        }
        return count;
    }

    // =========================================================
    // HÀM: findNextHumanHost
    // Mục đích:
    // - Tìm host người thật kế tiếp khi host hiện tại rời trận
    // =========================================================
    private Integer findNextHumanHost(MatchInstance match) {
        return match.sessions.keySet().stream().sorted().findFirst().orElse(null);
    }

    // =========================================================
    // HÀM: isOccupiedSlot
    // Mục đích:
    // - Kiểm tra 1 slot có đang bị chiếm bởi người thật hoặc bot không
    // =========================================================
    private boolean isOccupiedSlot(MatchInstance match, int playerId) {
        return match.sessions.containsKey(playerId) || isBotSlot(match, playerId);
    }

    // =========================================================
    // HÀM: isBotSlot
    // Mục đích:
    // - Kiểm tra slot có phải bot không
    // =========================================================
    private boolean isBotSlot(MatchInstance match, int playerId) {
        Player player = match.players.get(playerId);
        return player != null && player.bot;
    }

    // =========================================================
    // HÀM: canControlPlayer
    // Mục đích:
    // - Kiểm tra player có thể điều khiển được không
    // - áp dụng cho cả người thật và bot
    // =========================================================
    private boolean canControlPlayer(MatchInstance match, int playerId) {
        return match.sessions.containsKey(playerId) || isBotSlot(match, playerId);
    }

    // =========================================================
    // HÀM: isActiveParticipant
    // Mục đích:
    // - Kiểm tra player có còn là participant hợp lệ của trận không
    // =========================================================
    private boolean isActiveParticipant(MatchInstance match, Player player) {
        return player != null && (match.sessions.containsKey(player.id) || player.bot);
    }

    // =========================================================
    // HÀM: getParticipantCount
    // Mục đích:
    // - Đếm tổng participant hiện tại trong trận
    // =========================================================
    private int getParticipantCount(MatchInstance match) {
        int count = 0;
        for (int id = 1; id <= match.requiredPlayers; id++) {
            if (isOccupiedSlot(match, id)) {
                count++;
            }
        }
        return count;
    }

    // =========================================================
    // HÀM: getActivePlayers
    // Mục đích:
    // - Lấy danh sách player đang active
    // =========================================================
    private List<Player> getActivePlayers(MatchInstance match) {
        List<Player> list = new ArrayList<>();
        for (Player player : match.players.values()) {
            if (isActiveParticipant(match, player)) {
                list.add(player);
            }
        }
        return list;
    }

    // =========================================================
    // HÀM: createFreshPlayer
    // Mục đích:
    // - Tạo player mới với stat mặc định
    // =========================================================
    private Player createFreshPlayer(int playerId) {
        Player player = new Player(
                playerId,
                gameConfig.getSpawnRowForPlayer(playerId),
                gameConfig.getSpawnColForPlayer(playerId),
                Direction.down,
                gameConfig.getPlayer().getStartLives()
        );

        player.maxBombs = gameConfig.getPlayer().getStartMaxBombs();
        player.bombRange = gameConfig.getPlayer().getStartBombRange();
        player.speedLevel = gameConfig.getPlayer().getStartSpeedLevel();
        player.baseSpeedLevel = gameConfig.getPlayer().getStartSpeedLevel();

        player.speedBoostUntil = 0L;
        player.frozenUntil = 0L;
        player.nextBombRandom = false;
        player.nextBombFreeze = false;

        player.bombsPlaced = 0;
        player.kills = 0;
        player.deaths = 0;
        player.ovr = 0;

        player.userId = null;
        player.displayName = "Player-" + playerId;
        player.characterName = "Player " + playerId;
        player.gender = "";
        player.avatarCode = "";

        // Mặc định mỗi người 1 team riêng.
        // Khi join/reset sẽ được apply lại theo mode thực tế.
        player.teamId = "P" + playerId;

        player.bot = false;
        player.ready = false;
        player.botNextThinkAt = 0L;
        player.botBombCooldownUntil = 0L;

        player.inventory = new ArrayList<>();
        return player;
    }

    // =========================================================
    // HÀM: attachProfileToPlayer
    // Mục đích:
    // - Gắn profile thật của user vào player dựa trên JWT
    // =========================================================
    private void attachProfileToPlayer(MatchInstance match, WebSocketSession session, int playerId) {
        Player player = match.players.get(playerId);
        if (player == null) return;

        try {
            String token = extractTokenFromSession(session);
            if (token == null || token.isBlank()) return;
            if (!jwtService.isTokenValid(token)) return;

            String userId = jwtService.extractUserId(token);
            if (userId == null || userId.isBlank()) return;

            player.userId = userId;

            CharacterProfileDocument profile = characterProfileRepository.findByUserId(userId).orElse(null);
            if (profile == null) return;

            if (profile.getCharacterName() != null && !profile.getCharacterName().isBlank()) {
                player.characterName = profile.getCharacterName();
            }
            player.gender = profile.getGender() == null ? "" : profile.getGender();
            player.avatarCode = profile.getAvatarCode() == null ? "" : profile.getAvatarCode();
            player.displayName = player.characterName;
        } catch (Exception ignored) {
        }
    }

    // =========================================================
    // HÀM: extractTokenFromSession
    // Mục đích:
    // - Lấy token từ query param
    // =========================================================
    private String extractTokenFromSession(WebSocketSession session) {
        return extractQueryParam(session, "token");
    }

    // =========================================================
    // HÀM: extractRoomCodeFromSession
    // Mục đích:
    // - Lấy roomCode từ query param
    // =========================================================
    private String extractRoomCodeFromSession(WebSocketSession session) {
        String value = extractQueryParam(session, "roomCode");
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    // =========================================================
    // HÀM: extractRequiredPlayersFromSession
    // Mục đích:
    // - Lấy requiredPlayers từ query param
    // =========================================================
    private int extractRequiredPlayersFromSession(WebSocketSession session) {
        String value = extractQueryParam(session, "requiredPlayers");
        if (value == null || value.isBlank()) {
            return gameConfig.getMatch().getDefaultRequiredPlayers();
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return gameConfig.getMatch().getDefaultRequiredPlayers();
        }
    }

    // =========================================================
    // HÀM: extractHumanCountFromSession
    // Mục đích:
    // - Lấy humanCount từ query param
    // =========================================================
    private int extractHumanCountFromSession(WebSocketSession session) {
        String value = extractQueryParam(session, "humanCount");
        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    // =========================================================
    // HÀM: extractBotCountFromSession
    // Mục đích:
    // - Lấy botCount từ query param
    // =========================================================
    private int extractBotCountFromSession(WebSocketSession session) {
        String value = extractQueryParam(session, "botCount");
        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    // =========================================================
    // HÀM: extractQueryParam
    // Mục đích:
    // - Helper lấy value của 1 query param bất kỳ từ websocket URL
    // =========================================================
    private String extractQueryParam(WebSocketSession session, String key) {
        try {
            URI uri = session.getUri();
            if (uri == null || uri.getQuery() == null) return null;

            String[] parts = uri.getQuery().split("&");
            for (String part : parts) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2 && key.equals(kv[0])) {
                    return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // =========================================================
    // HÀM: reevaluateWaitingState
    // Mục đích:
    // - Tính lại trạng thái waiting / countdown trước khi trận bắt đầu
    // =========================================================
    private void reevaluateWaitingState(MatchInstance match) {
        int joined = getParticipantCount(match);

        if (match.gameStarted) {
            return;
        }

        if (joined >= match.requiredPlayers) {
            match.waitingForPlayers = false;
            if (match.countdownStartedAt == null) {
                match.countdownStartedAt = System.currentTimeMillis();
                match.countdownSeconds = gameConfig.getMatch().getStartCountdownSeconds();
            }
        } else {
            match.waitingForPlayers = true;
            match.countdownStartedAt = null;
            match.countdownSeconds = null;
        }
    }

    // =========================================================
    // HÀM: startMatchNow
    // Mục đích:
    // - Đánh dấu trận chính thức bắt đầu
    // =========================================================
    private void startMatchNow(MatchInstance match) {
        match.gameStarted = true;
        match.waitingForPlayers = false;
        match.countdownSeconds = 0;
        match.countdownStartedAt = null;
        match.resultMessage = "Trận đấu bắt đầu";
        match.matchStartedAt = Instant.now();
        match.historySaved = false;
    }

    // =========================================================
    // HÀM: getMoveCooldown
    // Mục đích:
    // - Lấy thời gian delay giữa 2 lần di chuyển theo speedLevel
    // =========================================================
    private long getMoveCooldown(Player player) {
        return gameConfig.getMoveCooldownForSpeedLevel(player.speedLevel);
    }

    // =========================================================
    // HÀM: updateOvr
    // Mục đích:
    // - Tính lại OVR cho 1 player
    // =========================================================
    private void updateOvr(Player player) {
        int score = player.kills * 100
                + player.lives * 50
                + player.bombsPlaced * 10
                - player.deaths * 20;
        player.ovr = Math.max(0, score);
    }

    // =========================================================
    // HÀM: updateAllOvr
    // Mục đích:
    // - Tính lại OVR cho toàn bộ player
    // =========================================================
    private void updateAllOvr(MatchInstance match) {
        for (Player p : match.players.values()) {
            if (p != null) {
                updateOvr(p);
            }
        }
    }

    // =========================================================
    // HÀM: isPlayerFrozen
    // Mục đích:
    // - Kiểm tra player có đang bị đông băng hay không
    // =========================================================
    private boolean isPlayerFrozen(Player player, long now) {
        return player.frozenUntil > 0 && now < player.frozenUntil;
    }

    // =========================================================
    // HÀM: handleMove (String)
    // Mục đích:
    // - Parse direction từ string rồi gọi sang handleMove(Direction)
    // =========================================================
    private void handleMove(MatchInstance match, int playerId, String directionRaw) {
        if (directionRaw == null) return;

        Direction direction;
        try {
            direction = Direction.valueOf(directionRaw);
        } catch (IllegalArgumentException ex) {
            return;
        }

        handleMove(match, playerId, direction);
    }

    // =========================================================
    // HÀM: handleMove (Direction)
    // Mục đích:
    // - Xử lý di chuyển thực tế của player
    // =========================================================
    private void handleMove(MatchInstance match, int playerId, Direction direction) {
        Player player = match.players.get(playerId);
        if (player == null) return;
        if (!canControlPlayer(match, playerId)) return;
        if (player.lives <= 0) return;

        long now = System.currentTimeMillis();
        if (isPlayerFrozen(player, now)) return;

        long last = match.lastMoveAt.getOrDefault(playerId, 0L);
        if (now - last < getMoveCooldown(player)) return;
        match.lastMoveAt.put(playerId, now);

        player.direction = direction;

        int nextRow = player.row;
        int nextCol = player.col;

        switch (direction) {
            case up -> nextRow--;
            case down -> nextRow++;
            case left -> nextCol--;
            case right -> nextCol++;
        }

        if (isWalkable(match, nextRow, nextCol, playerId)) {
            player.row = nextRow;
            player.col = nextCol;
            pickupItem(match, player);
        }
    }

    // =========================================================
    // HÀM: handlePlaceBomb
    // Mục đích:
    // - Đặt bom nếu player còn slot bom
    // =========================================================
    private void handlePlaceBomb(MatchInstance match, int playerId) {
        Player player = match.players.get(playerId);
        if (player == null) return;
        if (!canControlPlayer(match, playerId)) return;
        if (player.lives <= 0) return;

        long activeBombs = match.bombs.stream()
                .filter(b -> b.ownerId == playerId)
                .count();

        if (activeBombs >= player.maxBombs) return;

        boolean exists = match.bombs.stream()
                .anyMatch(b -> b.row == player.row && b.col == player.col);

        if (exists) return;

        boolean randomPattern = player.nextBombRandom;
        boolean freezeEffect = player.nextBombFreeze;

        match.bombs.add(new Bomb(
                UUID.randomUUID().toString(),
                playerId,
                player.row,
                player.col,
                System.currentTimeMillis(),
                player.bombRange,
                randomPattern,
                freezeEffect
        ));

        player.nextBombRandom = false;
        player.nextBombFreeze = false;

        player.bombsPlaced += 1;
        updateOvr(player);
    }

    // =========================================================
    // HÀM: handleUseBombSkill
    // Mục đích:
    // - Skill tăng maxBombs
    // - Áp dụng cho người thật và bot
    // =========================================================
    private void handleUseBombSkill(MatchInstance match, int playerId) {
        Player player = match.players.get(playerId);
        if (player == null) return;
        if (!canControlPlayer(match, playerId)) return;
        if (player.lives <= 0) return;

        player.maxBombs = Math.min(
                player.maxBombs + 1,
                gameConfig.getPlayer().getMaxBombs()
        );

        updateOvr(player);
    }

    // =========================================================
    // HÀM: handleUseSpeedSkill
    // Mục đích:
    // - Skill tăng tốc tạm thời trong 5 giây
    // =========================================================
    private void handleUseSpeedSkill(MatchInstance match, int playerId) {
        Player player = match.players.get(playerId);
        if (player == null) return;
        if (!canControlPlayer(match, playerId)) return;
        if (player.lives <= 0) return;

        long now = System.currentTimeMillis();

        int boostedSpeed = Math.min(
                player.baseSpeedLevel + 2,
                gameConfig.getPlayer().getMaxSpeedLevel()
        );

        player.speedLevel = boostedSpeed;
        player.speedBoostUntil = now + SPEED_SKILL_DURATION_MS;

        updateOvr(player);
    }

    // =========================================================
    // HÀM: handleUseItem
    // Mục đích:
    // - Dùng item trong inventory
    // =========================================================
    private void handleUseItem(MatchInstance match, int playerId, Integer slotIndex) {
        Player player = match.players.get(playerId);
        if (player == null || slotIndex == null) return;
        if (!canControlPlayer(match, playerId)) return;
        if (player.lives <= 0) return;
        if (slotIndex < 0 || slotIndex >= player.inventory.size()) return;

        ItemType item = player.inventory.remove((int) slotIndex);
        long now = System.currentTimeMillis();

        switch (item) {
            case BOMB_UP -> player.maxBombs = Math.min(
                    player.maxBombs + 1,
                    gameConfig.getPlayer().getMaxBombs()
            );

            case FLAME_UP -> player.bombRange = Math.min(
                    player.bombRange + 1,
                    gameConfig.getPlayer().getMaxBombRange()
            );

            case SPEED_UP -> {
                player.baseSpeedLevel = Math.min(
                        player.baseSpeedLevel + 1,
                        gameConfig.getPlayer().getMaxSpeedLevel()
                );

                if (player.speedBoostUntil <= now) {
                    player.speedLevel = player.baseSpeedLevel;
                } else {
                    player.speedLevel = Math.max(player.speedLevel, player.baseSpeedLevel);
                }
            }

            case SHIELD -> player.invulnerableUntil = Math.max(
                    player.invulnerableUntil,
                    now + gameConfig.getPlayer().getShieldDurationMs()
            );

            case HEART -> player.lives = Math.min(
                    player.lives + 1,
                    gameConfig.getPlayer().getMaxLives()
            );

            case TELEPORT -> {
                teleportPlayerToRandomSafeTile(match, player);
                pickupItem(match, player);
            }

            case RANDOM_BOMB -> player.nextBombRandom = true;
            case FREEZE_BOMB -> player.nextBombFreeze = true;
        }

        updateOvr(player);
    }

    // =========================================================
    // HÀM: tick
    // Mục đích:
    // - Tick game chính theo fixedRate
    // - xử lý countdown
    // - hết hiệu ứng
    // - bot
    // - bom nổ
    // - explosion hết hạn
    // - damage / freeze
    // =========================================================
    @Scheduled(fixedRateString = "${game.match.tick-rate-ms:100}")
    public synchronized void tick() {
        List<String> matchKeys = new ArrayList<>(matches.keySet());

        for (String matchKey : matchKeys) {
            MatchInstance match = matches.get(matchKey);
            if (match == null) continue;

            boolean changed = false;
            long now = System.currentTimeMillis();

            // Countdown trước khi bắt đầu trận
            if (!match.gameStarted && match.countdownStartedAt != null) {
                long elapsed = now - match.countdownStartedAt;
                int remain = gameConfig.getMatch().getStartCountdownSeconds() - (int) (elapsed / 1000);

                if (remain < 0) remain = 0;

                if (!Objects.equals(match.countdownSeconds, remain)) {
                    match.countdownSeconds = remain;
                    changed = true;
                }

                if (elapsed >= gameConfig.getMatch().getStartCountdownSeconds() * 1000L) {
                    startMatchNow(match);
                    changed = true;
                }
            }

            if (!match.gameStarted) {
                if (changed) {
                    broadcastState(matchKey);
                }
                continue;
            }

            boolean gameChanged = false;

            // Hết hiệu ứng speed skill
            for (Player player : match.players.values()) {
                if (player == null) continue;

                if (player.speedBoostUntil > 0 && now >= player.speedBoostUntil) {
                    player.speedBoostUntil = 0L;
                    player.speedLevel = player.baseSpeedLevel;
                    gameChanged = true;
                }
            }

            // Hết đóng băng
            for (Player player : match.players.values()) {
                if (player == null) continue;

                if (player.frozenUntil > 0 && now >= player.frozenUntil) {
                    player.frozenUntil = 0L;
                    gameChanged = true;
                }
            }

            // Tick bot
            if (updateBots(match, now)) {
                gameChanged = true;
            }

            // Bom nổ
            List<Bomb> expired = match.bombs.stream()
                    .filter(b -> now - b.placedAt >= gameConfig.getTiming().getBombFuseMs())
                    .toList();

            if (!expired.isEmpty()) {
                detonateBombs(match, expired, now);
                gameChanged = true;
            }

            // Xóa explosion hết hạn
            int beforeExplosions = match.explosions.size();
            match.explosions.removeIf(e -> now - e.startedAt >= e.duration);
            if (beforeExplosions != match.explosions.size()) {
                gameChanged = true;
            }

            // Dính damage / freeze
            if (applyDamageOrFreeze(match, now)) {
                gameChanged = true;
            }

            if (changed || gameChanged) {
                broadcastState(matchKey);
            }
        }
    }

    // =========================================================
    // HÀM: updateBots
    // Mục đích:
    // - Tính quyết định của bot mỗi tick
    // - dùng skill bomb
    // - dùng skill speed
    // - dùng item
    // - đặt bom
    // - di chuyển
    // =========================================================
    private boolean updateBots(MatchInstance match, long now) {
        if (!match.customRoom) return false;
        if (!match.gameStarted || match.gameOver) return false;

        boolean changed = false;
        List<Player> activePlayers = getActivePlayers(match);

        for (Player bot : activePlayers) {
            if (bot == null || !bot.bot || bot.lives <= 0) continue;

            BotService.BotDecision decision = botService.decide(
                    match.board,
                    activePlayers,
                    match.bombs,
                    match.explosions,
                    match.items,
                    bot,
                    now
            );

            // 1) dùng skill tăng bom
            if (decision.useSkillBomb()) {
                int before = bot.maxBombs;
                handleUseBombSkill(match, bot.id);
                if (bot.maxBombs != before) {
                    changed = true;
                }
            }

            // 2) dùng skill tăng tốc
            if (decision.useSkillSpeed()) {
                int beforeSpeed = bot.speedLevel;
                long beforeUntil = bot.speedBoostUntil;
                handleUseSpeedSkill(match, bot.id);
                if (bot.speedLevel != beforeSpeed || bot.speedBoostUntil != beforeUntil) {
                    changed = true;
                }
            }

            // 3) dùng item
            if (decision.useItemSlot() != null) {
                int beforeInv = bot.inventory.size();
                handleUseItem(match, bot.id, decision.useItemSlot());
                if (bot.inventory.size() != beforeInv) {
                    changed = true;
                }
            }

            // 4) đặt bom
            if (decision.placeBomb()) {
                int beforeBombs = match.bombs.size();
                handlePlaceBomb(match, bot.id);
                if (match.bombs.size() != beforeBombs) {
                    changed = true;

                    // Bot vừa đặt bom xong thì reset think time
                    // để nó đổi sang mode chạy thoát ngay
                    bot.botNextThinkAt = 0L;
                }
            }

            // 5) di chuyển
            if (decision.move() != null) {
                int oldRow = bot.row;
                int oldCol = bot.col;
                handleMove(match, bot.id, decision.move());
                if (bot.row != oldRow || bot.col != oldCol) {
                    changed = true;
                }
            }
        }

        return changed;
    }

    // =========================================================
    // HÀM: detonateBombs
    // Mục đích:
    // - Xử lý nổ bom
    // - bao gồm nổ dây chuyền
    // - phá tường mềm
    // - drop item
    // - tạo explosion
    // =========================================================
    private void detonateBombs(MatchInstance match, List<Bomb> expired, long now) {
        Queue<Bomb> queue = new ArrayDeque<>(expired);
        Set<String> detonatedIds = new HashSet<>();

        while (!queue.isEmpty()) {
            Bomb bomb = queue.poll();
            if (!detonatedIds.add(bomb.id)) continue;

            List<FlameCell> cells = buildExplosionCells(match, bomb);

            for (FlameCell cell : cells) {
                if (match.board[cell.row][cell.col] == 2) {
                    match.board[cell.row][cell.col] = 0;
                    maybeDropItem(match, cell.row, cell.col);
                }
            }

            match.explosions.add(new Explosion(
                    bomb.id,
                    bomb.ownerId,
                    bomb.row,
                    bomb.col,
                    now,
                    gameConfig.getTiming().getExplosionMs(),
                    cells,
                    bomb.randomPattern,
                    bomb.freezeEffect
            ));

            Set<String> flameSet = new HashSet<>();
            for (FlameCell cell : cells) {
                flameSet.add(cell.row + ":" + cell.col);
            }

            for (Bomb other : match.bombs) {
                if (detonatedIds.contains(other.id)) continue;
                if (flameSet.contains(other.row + ":" + other.col)) {
                    queue.offer(other);
                }
            }
        }

        match.bombs.removeIf(b -> detonatedIds.contains(b.id));
    }

    // =========================================================
    // HÀM: applyDamageOrFreeze
    // Mục đích:
    // - Áp damage hoặc đóng băng cho player trúng explosion
    // - ở DUO: đồng đội không gây damage / freeze cho nhau
    // - sau đó kiểm tra thắng thua
    // =========================================================
    private boolean applyDamageOrFreeze(MatchInstance match, long now) {
        boolean changed = false;

        for (Player victim : match.players.values()) {
            if (!isActiveParticipant(match, victim)) continue;
            if (victim.lives <= 0) continue;
            if (now < victim.invulnerableUntil) continue;

            boolean hitNormalBomb = false;
            boolean hitFreezeBomb = false;
            Integer killerId = null;

            outer:
            for (Explosion explosion : match.explosions) {
                Player attacker = match.players.get(explosion.ownerId);

                for (FlameCell cell : explosion.cells) {
                    if (cell.row != victim.row || cell.col != victim.col) {
                        continue;
                    }

                    // DUO: đồng đội không gây hiệu ứng cho nhau
                    if (isBlockedFriendlyFire(match, attacker, victim)) {
                        continue;
                    }

                    if (explosion.freezeEffect) {
                        hitFreezeBomb = true;
                    } else {
                        hitNormalBomb = true;
                        killerId = explosion.ownerId;
                        break outer;
                    }
                }
            }

            if (!hitNormalBomb && !hitFreezeBomb) {
                continue;
            }

            if (hitNormalBomb) {
                victim.lives -= 1;
                victim.deaths += 1;
                victim.invulnerableUntil = now + gameConfig.getTiming().getInvulnerableMs();
                updateOvr(victim);

                if (killerId != null && killerId != victim.id) {
                    Player killer = match.players.get(killerId);
                    if (killer != null) {
                        killer.kills += 1;
                        updateOvr(killer);
                    }
                }

                if (victim.lives > 0) {
                    respawn(victim);
                } else {
                    victim.row = -99;
                    victim.col = -99;
                }

                changed = true;
                continue;
            }

            victim.frozenUntil = Math.max(
                    victim.frozenUntil,
                    now + gameConfig.getTiming().getFreezeDurationMs()
            );
            changed = true;
        }

        if (changed) {
            checkWinner(match);
        }

        return changed;
    }

    // =========================================================
    // HÀM: checkWinner
    // Mục đích:
    // - Kiểm tra trận đã có người / đội thắng chưa
    //
    // SOLO:
    // - còn 1 player sống cuối cùng
    //
    // DUO:
    // - còn 1 team sống cuối cùng
    // =========================================================
    private void checkWinner(MatchInstance match) {
        if (match.gameOver) return;
        if (!match.gameStarted) return;

        List<Player> alivePlayers = match.players.values().stream()
                .filter(p -> isActiveParticipant(match, p))
                .filter(p -> p.lives > 0)
                .toList();

        Map<String, List<Player>> aliveTeams = new LinkedHashMap<>();
        for (Player alive : alivePlayers) {
            String teamId = alive.teamId == null || alive.teamId.isBlank()
                    ? "P" + alive.id
                    : alive.teamId;
            aliveTeams.computeIfAbsent(teamId, key -> new ArrayList<>()).add(alive);
        }

        if (aliveTeams.size() == 1) {
            Map.Entry<String, List<Player>> winnerEntry = aliveTeams.entrySet().iterator().next();
            List<Player> winners = winnerEntry.getValue();
            Player representativeWinner = winners.get(0);

            match.gameOver = true;
            match.winnerId = representativeWinner.id;

            if (MATCH_MODE_DUO.equals(match.matchMode)) {
                match.resultMessage = getTeamDisplayName(winnerEntry.getKey()) + " thắng trận";
            } else {
                match.resultMessage = representativeWinner.characterName + " thắng trận";
            }

            updateAllOvr(match);
            saveMatchHistoryIfNeeded(match);
            finishLobbyRoomIfNeeded(match);
        } else if (aliveTeams.isEmpty()) {
            match.gameOver = true;
            match.winnerId = null;
            match.resultMessage = "Hòa - không còn người sống";
            updateAllOvr(match);
            saveMatchHistoryIfNeeded(match);
            finishLobbyRoomIfNeeded(match);
        }
    }

    // =========================================================
    // HÀM: finishLobbyRoomIfNeeded
    // Mục đích:
    // - Nếu đây là custom room thì xóa room khỏi lobby
    // - Không tác động đến quick play
    // =========================================================
    private void finishLobbyRoomIfNeeded(MatchInstance match) {
        if (match == null) return;
        if (!match.customRoom) return;
        if (match.matchKey == null || match.matchKey.isBlank()) return;

        roomLobbyService.finishRoom(match.matchKey);
    }

    // =========================================================
    // HÀM: saveMatchHistoryIfNeeded
    // Mục đích:
    // - Lưu lịch sử trận nếu chưa lưu
    //
    // Lưu ý DUO:
    // - winnerId chỉ là 1 người đại diện của team thắng
    // - flag winner trong từng player sẽ set theo teamId
    // =========================================================
    private void saveMatchHistoryIfNeeded(MatchInstance match) {
        if (!match.gameOver || match.historySaved) return;

        try {
            MatchHistoryDocument history = new MatchHistoryDocument();
            history.setRoomCode(match.matchKey);
            history.setStartedAt(match.matchStartedAt != null ? match.matchStartedAt : Instant.now());
            history.setEndedAt(Instant.now());

            List<String> participantUserIds = new ArrayList<>();
            List<MatchHistoryDocument.PlayerMatchResult> results = new ArrayList<>();

            Player winner = match.winnerId != null ? match.players.get(match.winnerId) : null;
            String winningTeamId = winner != null ? winner.teamId : null;

            if (winner != null) {
                history.setWinnerUserId(winner.userId);

                if (MATCH_MODE_DUO.equals(match.matchMode)) {
                    history.setWinnerCharacterName(getTeamDisplayName(winningTeamId));
                } else {
                    history.setWinnerCharacterName(winner.characterName);
                }
            }

            for (Player player : match.players.values()) {
                if (player == null) continue;
                if (player.bot) continue;
                if (player.userId == null || player.userId.isBlank()) continue;

                if (!participantUserIds.contains(player.userId)) {
                    participantUserIds.add(player.userId);
                }

                MatchHistoryDocument.PlayerMatchResult result =
                        new MatchHistoryDocument.PlayerMatchResult();
                result.setUserId(player.userId);
                result.setCharacterName(player.characterName);
                result.setBombsPlaced(player.bombsPlaced);
                result.setKills(player.kills);
                result.setDeaths(player.deaths);
                result.setLivesLeft(Math.max(player.lives, 0));
                result.setOvr(player.ovr);

                boolean isWinner;
                if (winner == null) {
                    isWinner = false;
                } else if (MATCH_MODE_DUO.equals(match.matchMode)) {
                    isWinner = Objects.equals(player.teamId, winningTeamId);
                } else {
                    isWinner = Objects.equals(player.id, match.winnerId);
                }

                result.setWinner(isWinner);
                results.add(result);
            }

            history.setParticipantUserIds(participantUserIds);
            history.setPlayers(results);

            historyService.saveHistory(history);
            match.historySaved = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================================================
    // HÀM: respawn
    // Mục đích:
    // - Hồi sinh player về vị trí spawn
    // - reset trạng thái tạm thời
    // =========================================================
    private void respawn(Player player) {
        player.row = gameConfig.getSpawnRowForPlayer(player.id);
        player.col = gameConfig.getSpawnColForPlayer(player.id);
        player.direction = Direction.down;

        player.speedBoostUntil = 0L;
        player.speedLevel = player.baseSpeedLevel;

        player.frozenUntil = 0L;
        player.nextBombRandom = false;
        player.nextBombFreeze = false;
    }

    // =========================================================
    // HÀM: pickupItem
    // Mục đích:
    // - Nhặt item tại ô đang đứng nếu còn chỗ trong inventory
    // =========================================================
    private void pickupItem(MatchInstance match, Player player) {
        if (player.inventory.size() >= gameConfig.getPlayer().getMaxInventorySize()) return;

        Item found = null;
        for (Item item : match.items) {
            if (item.row == player.row && item.col == player.col) {
                found = item;
                break;
            }
        }

        if (found != null) {
            player.inventory.add(found.type);
            match.items.remove(found);
        }
    }

    // =========================================================
    // HÀM: maybeDropItem
    // Mục đích:
    // - Sau khi phá tường mềm thì có tỉ lệ rơi item
    // =========================================================
    private void maybeDropItem(MatchInstance match, int row, int col) {
        boolean exists = match.items.stream().anyMatch(i -> i.row == row && i.col == col);
        if (exists) return;

        if (random.nextDouble() > gameConfig.getDrop().getItemRate()) return;

        ItemType[] pool = {
                ItemType.BOMB_UP,
                ItemType.FLAME_UP,
                ItemType.SPEED_UP,
                ItemType.SHIELD,
                ItemType.HEART,
                ItemType.TELEPORT,
                ItemType.RANDOM_BOMB,
                ItemType.FREEZE_BOMB
        };

        ItemType picked = pool[random.nextInt(pool.length)];
        match.items.add(new Item(UUID.randomUUID().toString(), row, col, picked));
    }

    // =========================================================
    // HÀM: isWalkable
    // Mục đích:
    // - Kiểm tra ô có đi được không
    // =========================================================
    private boolean isWalkable(MatchInstance match, int row, int col, int movingPlayerId) {
        if (!inBounds(row, col)) return false;
        if (match.board[row][col] != 0) return false;

        boolean bombBlocked = match.bombs.stream().anyMatch(b -> b.row == row && b.col == col);
        if (bombBlocked) return false;

        for (Player player : match.players.values()) {
            if (!isActiveParticipant(match, player)) continue;
            if (player.lives <= 0) continue;
            if (player.id != movingPlayerId && player.row == row && player.col == col) {
                return false;
            }
        }

        return true;
    }

    // =========================================================
    // HÀM: teleportPlayerToRandomSafeTile
    // Mục đích:
    // - Dịch chuyển player đến ô an toàn ngẫu nhiên
    // =========================================================
    private void teleportPlayerToRandomSafeTile(MatchInstance match, Player player) {
        List<int[]> candidates = new ArrayList<>();

        for (int row = 1; row < gameConfig.getBoard().getRows() - 1; row++) {
            for (int col = 1; col < gameConfig.getBoard().getCols() - 1; col++) {
                if (canTeleportTo(match, row, col, player.id)) {
                    if (row != player.row || col != player.col) {
                        candidates.add(new int[]{row, col});
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return;
        }

        int[] target = candidates.get(random.nextInt(candidates.size()));
        player.row = target[0];
        player.col = target[1];
    }

    // =========================================================
    // HÀM: canTeleportTo
    // Mục đích:
    // - Kiểm tra ô có thể teleport đến được không
    // =========================================================
    private boolean canTeleportTo(MatchInstance match, int row, int col, int movingPlayerId) {
        if (!inBounds(row, col)) return false;
        if (match.board[row][col] != 0) return false;

        boolean bombBlocked = match.bombs.stream().anyMatch(b -> b.row == row && b.col == col);
        if (bombBlocked) return false;

        for (Player player : match.players.values()) {
            if (!isActiveParticipant(match, player)) continue;
            if (player.lives <= 0) continue;
            if (player.id != movingPlayerId && player.row == row && player.col == col) {
                return false;
            }
        }

        return true;
    }

    // =========================================================
    // HÀM: buildExplosionCells
    // Mục đích:
    // - Chọn kiểu explosion:
    //   + normal
    //   + random
    // =========================================================
    private List<FlameCell> buildExplosionCells(MatchInstance match, Bomb bomb) {
        if (bomb.randomPattern) {
            return buildRandomExplosionCells(match, bomb.row, bomb.col, bomb.range);
        }
        return buildNormalExplosionCells(match, bomb.row, bomb.col, bomb.range);
    }

    // =========================================================
    // HÀM: buildNormalExplosionCells
    // Mục đích:
    // - Tạo flame cell theo hình nổ chuẩn 4 hướng
    // =========================================================
    private List<FlameCell> buildNormalExplosionCells(MatchInstance match, int row, int col, int range) {
        List<FlameCell> cells = new ArrayList<>();
        cells.add(new FlameCell(row, col, "center"));

        int[][] dirs = {
                {0, -1},
                {0, 1},
                {-1, 0},
                {1, 0}
        };

        String[] mids = {"horizontal", "horizontal", "vertical", "vertical"};
        String[] ends = {"left_end", "right_end", "top_end", "bottom_end"};

        for (int d = 0; d < dirs.length; d++) {
            int dr = dirs[d][0];
            int dc = dirs[d][1];

            for (int i = 1; i <= range; i++) {
                int nr = row + dr * i;
                int nc = col + dc * i;

                if (!inBounds(nr, nc)) break;
                if (match.board[nr][nc] == 1) break;

                boolean isBreakable = match.board[nr][nc] == 2;
                boolean isEnd = i == range || isBreakable;

                cells.add(new FlameCell(nr, nc, isEnd ? ends[d] : mids[d]));

                if (isBreakable) break;
            }
        }

        return cells;
    }

    // =========================================================
    // HÀM: buildRandomExplosionCells
    // Mục đích:
    // - Tạo flame cell ngẫu nhiên cho random bomb
    // =========================================================
    private List<FlameCell> buildRandomExplosionCells(MatchInstance match, int row, int col, int range) {
        List<FlameCell> cells = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        List<int[]> frontier = new ArrayList<>();

        cells.add(new FlameCell(row, col, "center"));
        visited.add(row + ":" + col);
        frontier.add(new int[]{row, col});

        int[][] dirs = {
                {0, -1},
                {0, 1},
                {-1, 0},
                {1, 0}
        };

        int stepCount = Math.max(4, range * 4);

        for (int i = 0; i < stepCount; i++) {
            if (frontier.isEmpty()) break;

            int[] origin = frontier.get(random.nextInt(frontier.size()));
            int[] dir = dirs[random.nextInt(dirs.length)];

            int nr = origin[0] + dir[0];
            int nc = origin[1] + dir[1];

            if (!inBounds(nr, nc)) continue;
            if (match.board[nr][nc] == 1) continue;

            String key = nr + ":" + nc;
            if (!visited.add(key)) continue;

            cells.add(new FlameCell(nr, nc, "center"));

            if (match.board[nr][nc] != 2) {
                frontier.add(new int[]{nr, nc});
            }
        }

        return cells;
    }

    // =========================================================
    // HÀM: inBounds
    // Mục đích:
    // - Kiểm tra ô có nằm trong biên map không
    // =========================================================
    private boolean inBounds(int row, int col) {
        return row >= 0 && row < gameConfig.getBoard().getRows()
                && col >= 0 && col < gameConfig.getBoard().getCols();
    }

    // =========================================================
    // HÀM: createInitialBoard
    // Mục đích:
    // - Tạo map ban đầu
    // - sinh hard wall / soft wall
    // - giữ vùng spawn an toàn
    // =========================================================
    private int[][] createInitialBoard() {
        int rows = gameConfig.getBoard().getRows();
        int cols = gameConfig.getBoard().getCols();
        int[][] newBoard = new int[rows][cols];

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (row == 0 || col == 0 || row == rows - 1 || col == cols - 1) {
                    newBoard[row][col] = 1;
                } else if (row % 2 == 0 && col % 2 == 0) {
                    newBoard[row][col] = 1;
                } else {
                    newBoard[row][col] = 0;
                }
            }
        }

        Set<String> safe = new HashSet<>();

        int p1Row = gameConfig.getSpawn().getP1Row();
        int p1Col = gameConfig.getSpawn().getP1Col();
        int p2Row = gameConfig.getSpawn().getP2Row();
        int p2Col = gameConfig.getSpawn().getP2Col();
        int p3Row = gameConfig.getSpawn().getP3Row();
        int p3Col = gameConfig.getSpawn().getP3Col();
        int p4Row = gameConfig.getSpawn().getP4Row();
        int p4Col = gameConfig.getSpawn().getP4Col();

        safe.add(p1Row + ":" + p1Col);
        safe.add(p1Row + ":" + (p1Col + 1));
        safe.add((p1Row + 1) + ":" + p1Col);

        safe.add(p2Row + ":" + p2Col);
        safe.add(p2Row + ":" + (p2Col - 1));
        safe.add((p2Row + 1) + ":" + p2Col);

        safe.add(p3Row + ":" + p3Col);
        safe.add(p3Row + ":" + (p3Col + 1));
        safe.add((p3Row - 1) + ":" + p3Col);

        safe.add(p4Row + ":" + p4Col);
        safe.add(p4Row + ":" + (p4Col - 1));
        safe.add((p4Row - 1) + ":" + p4Col);

        for (int row = 1; row < rows - 1; row++) {
            for (int col = 1; col < cols - 1; col++) {
                if (newBoard[row][col] != 0) continue;
                if (safe.contains(row + ":" + col)) continue;

                if (random.nextDouble() < gameConfig.getBoard().getSoftWallRate()) {
                    newBoard[row][col] = 2;
                }
            }
        }

        return newBoard;
    }

    // =========================================================
    // HÀM: resetPlayersOnly
    // Mục đích:
    // - Reset player về trạng thái đầu trận
    // - chỉ reset đúng số slot của trận hiện tại
    // - đồng thời gán lại team theo mode hiện tại
    // =========================================================
    private void resetPlayersOnly(MatchInstance match) {
        match.players.clear();

        for (int id = 1; id <= match.requiredPlayers; id++) {
            Player freshPlayer = createFreshPlayer(id);
            applyMatchModeToPlayer(match, freshPlayer);
            match.players.put(id, freshPlayer);
        }

        for (Integer playerId : match.sessions.keySet()) {
            WebSocketSession session = match.sessions.get(playerId);
            if (session != null) {
                attachProfileToPlayer(match, session, playerId);
            }
        }

        updateAllOvr(match);
    }

    // =========================================================
    // HÀM: resetMatch
    // Mục đích:
    // - Reset toàn bộ trận:
    //   + board
    //   + bombs
    //   + explosions
    //   + items
    //   + trạng thái gameOver / waiting / countdown
    // =========================================================
    private void resetMatch(MatchInstance match) {
        match.board = createInitialBoard();
        match.bombs.clear();
        match.explosions.clear();
        match.items.clear();
        match.lastMoveAt.clear();

        match.gameOver = false;
        match.winnerId = null;
        match.resultMessage = null;

        match.gameStarted = false;
        match.waitingForPlayers = true;
        match.countdownSeconds = null;
        match.countdownStartedAt = null;

        match.matchStartedAt = null;
        match.historySaved = false;

        resetPlayersOnly(match);
    }

    // =========================================================
    // HÀM: broadcastState
    // Mục đích:
    // - Gửi state hiện tại của trận cho toàn bộ session trong match
    // =========================================================
    public synchronized void broadcastState(String matchKey) {
        MatchInstance match = matches.get(matchKey);
        if (match == null) return;

        GameState state = new GameState(
                copyBoard(match),
                copyPlayers(match),
                copyBombs(match),
                copyExplosions(match),
                copyItems(match),
                match.gameOver,
                match.winnerId,
                match.resultMessage,
                match.waitingForPlayers,
                match.gameStarted,
                getParticipantCount(match),
                match.requiredPlayers,
                match.countdownSeconds,
                match.matchMode
        );

        String json;
        try {
            json = objectMapper.writeValueAsString(new ServerMessage("state", state));
        } catch (Exception e) {
            return;
        }

        for (WebSocketSession session : match.sessions.values()) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            } catch (IOException ignored) {
            }
        }
    }

    // =========================================================
    // HÀM: send
    // Mục đích:
    // - Gửi 1 websocket message đến 1 session
    // =========================================================
    private void send(WebSocketSession session, ServerMessage message) {
        try {
            if (session != null && session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            }
        } catch (IOException ignored) {
        }
    }

    // =========================================================
    // HÀM: copyBoard
    // Mục đích:
    // - Copy board để gửi ra ngoài
    // =========================================================
    private int[][] copyBoard(MatchInstance match) {
        int rows = gameConfig.getBoard().getRows();
        int cols = gameConfig.getBoard().getCols();
        int[][] copied = new int[rows][cols];

        for (int i = 0; i < rows; i++) {
            System.arraycopy(match.board[i], 0, copied[i], 0, cols);
        }

        return copied;
    }

    // =========================================================
    // HÀM: copyPlayers
    // Mục đích:
    // - Copy player list để gửi ra ngoài
    // =========================================================
    private List<Player> copyPlayers(MatchInstance match) {
        List<Player> list = new ArrayList<>();

        for (Player p : match.players.values()) {
            if (!isActiveParticipant(match, p)) continue;

            Player copy = new Player(p.id, p.row, p.col, p.direction, p.lives);
            copy.invulnerableUntil = p.invulnerableUntil;

            copy.maxBombs = p.maxBombs;
            copy.bombRange = p.bombRange;
            copy.speedLevel = p.speedLevel;
            copy.baseSpeedLevel = p.baseSpeedLevel;
            copy.speedBoostUntil = p.speedBoostUntil;

            copy.frozenUntil = p.frozenUntil;
            copy.nextBombRandom = p.nextBombRandom;
            copy.nextBombFreeze = p.nextBombFreeze;

            copy.bombsPlaced = p.bombsPlaced;
            copy.kills = p.kills;
            copy.deaths = p.deaths;
            copy.ovr = p.ovr;

            copy.userId = p.userId;
            copy.displayName = p.displayName;
            copy.characterName = p.characterName;
            copy.gender = p.gender;
            copy.avatarCode = p.avatarCode;
            copy.teamId = p.teamId;

            copy.bot = p.bot;
            copy.ready = p.ready;
            copy.botNextThinkAt = p.botNextThinkAt;
            copy.botBombCooldownUntil = p.botBombCooldownUntil;

            copy.inventory = new ArrayList<>(p.inventory);
            list.add(copy);
        }

        list.sort(Comparator.comparingInt(p -> p.id));
        return list;
    }

    // =========================================================
    // HÀM: copyBombs
    // Mục đích:
    // - Copy bomb list để gửi ra ngoài
    // =========================================================
    private List<Bomb> copyBombs(MatchInstance match) {
        List<Bomb> list = new ArrayList<>();

        for (Bomb b : match.bombs) {
            list.add(new Bomb(
                    b.id,
                    b.ownerId,
                    b.row,
                    b.col,
                    b.placedAt,
                    b.range,
                    b.randomPattern,
                    b.freezeEffect
            ));
        }

        return list;
    }

    // =========================================================
    // HÀM: copyExplosions
    // Mục đích:
    // - Copy explosion list để gửi ra ngoài
    // =========================================================
    private List<Explosion> copyExplosions(MatchInstance match) {
        List<Explosion> list = new ArrayList<>();

        for (Explosion e : match.explosions) {
            List<FlameCell> copiedCells = new ArrayList<>();
            for (FlameCell c : e.cells) {
                copiedCells.add(new FlameCell(c.row, c.col, c.kind));
            }

            list.add(new Explosion(
                    e.id,
                    e.ownerId,
                    e.row,
                    e.col,
                    e.startedAt,
                    e.duration,
                    copiedCells,
                    e.randomPattern,
                    e.freezeEffect
            ));
        }

        return list;
    }

    // =========================================================
    // HÀM: copyItems
    // Mục đích:
    // - Copy item list để gửi ra ngoài
    // =========================================================
    private List<Item> copyItems(MatchInstance match) {
        List<Item> list = new ArrayList<>();

        for (Item i : match.items) {
            list.add(new Item(i.id, i.row, i.col, i.type));
        }

        return list;
    }

    // =========================================================
    // CLASS PHỤ: MatchInstance
    // Mục đích:
    // - Lưu trạng thái nội bộ của 1 trận
    // =========================================================
    private static class MatchInstance {
        String matchKey;
        int requiredPlayers;

        // room lobby truyền sang:
        // - cần bao nhiêu người thật
        // - cần bao nhiêu bot
        int expectedHumanCount;
        int expectedBotCount;

        boolean customRoom;
        Integer hostPlayerId;

        // SOLO hoặc DUO
        String matchMode = MATCH_MODE_SOLO;

        Map<Integer, WebSocketSession> sessions = new ConcurrentHashMap<>();
        Map<Integer, Player> players = new HashMap<>();
        Map<Integer, Long> lastMoveAt = new HashMap<>();

        int[][] board;
        List<Bomb> bombs = new ArrayList<>();
        List<Explosion> explosions = new ArrayList<>();
        List<Item> items = new ArrayList<>();

        boolean gameOver = false;
        Integer winnerId = null;
        String resultMessage = null;

        boolean gameStarted = false;
        boolean waitingForPlayers = true;
        Integer countdownSeconds = null;
        Long countdownStartedAt = null;

        Instant matchStartedAt = null;
        boolean historySaved = false;
    }
}
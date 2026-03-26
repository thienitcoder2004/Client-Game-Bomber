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

/**
 * Service quản lý toàn bộ logic trận đấu real-time.
 *
 * Nhiệm vụ chính:
 * - Cho player join / leave match
 * - Quản lý quick play và custom room
 * - Xử lý move / bomb / item / skill
 * - Tick game theo chu kỳ
 * - Điều khiển bot
 * - Kiểm tra thắng thua
 * - Lưu lịch sử trận đấu
 * - Broadcast trạng thái game cho client qua WebSocket
 */
@Service
public class GameRoomService {

    // =========================================================
    // QUICK_PLAY:
    // - Chế độ chơi nhanh
    // - Không dùng room lobby riêng như custom room
    // - Hệ thống tự gom người chơi vào cùng 1 queue
    // =========================================================
    private static final String QUICK_PLAY_KEY = "QUICK-PLAY";

    // =========================================================
    // MATCH MODE:
    // - SOLO = mỗi người 1 team riêng
    // - DUO  = chơi theo đội 2vs2
    // =========================================================
    private static final String MATCH_MODE_SOLO = "SOLO";
    private static final String MATCH_MODE_DUO = "DUO";

    // =========================================================
    // SPEED_SKILL_DURATION_MS:
    // - Thời gian skill tăng tốc tồn tại
    // - Hiện tại là 5 giây
    // =========================================================
    private static final long SPEED_SKILL_DURATION_MS = 5000L;

    /**
     * Dùng để convert object -> JSON khi gửi WebSocket message.
     */
    private final ObjectMapper objectMapper;

    /**
     * Chứa toàn bộ config game đọc từ application.properties.
     */
    private final GameConfigProperties gameConfig;

    /**
     * Random dùng cho:
     * - rơi item
     * - chọn vị trí teleport
     * - pattern random bomb
     */
    private final Random random = new Random();

    /**
     * Service xử lý JWT.
     * Dùng để đọc token từ query param khi WebSocket connect.
     */
    private final JwtService jwtService;

    /**
     * Repository lấy profile nhân vật của user.
     */
    private final CharacterProfileRepository characterProfileRepository;

    /**
     * Service lưu lịch sử trận đấu.
     */
    private final HistoryService historyService;

    /**
     * Service xử lý AI decision của bot.
     */
    private final BotService botService;

    /**
     * Service quản lý room trong lobby.
     * Dùng để finish room khi trận kết thúc hoặc khi mọi người rời đi.
     */
    private final RoomLobbyService roomLobbyService;

    // =========================================================
    // matches:
    // - key   = matchKey / roomCode
    // - value = trạng thái nội bộ của 1 trận
    //
    // Ví dụ:
    // - QUICK-PLAY-SOLO
    // - QUICK-PLAY-DUO
    // - ROOM123
    // =========================================================
    private final Map<String, MatchInstance> matches = new ConcurrentHashMap<>();

    /**
     * Constructor inject các dependency cần dùng.
     */
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
    // Luồng xử lý:
    // 1. Đọc roomCode / requiredPlayers / humanCount / botCount / matchMode
    //    từ query param của websocket URL
    // 2. Nếu không có roomCode => xem như quick play
    // 3. Tạo match nếu chưa có
    // 4. Tìm slot trống đầu tiên
    // 5. Tạo player mới, gán team, attach profile
    // 6. Nếu custom room và đủ người thật thì sync bot theo cấu hình
    // 7. Tính lại trạng thái chờ / countdown
    //
    // Giá trị trả về:
    // - playerId nếu vào thành công
    // - null nếu phòng đã đầy
    // =========================================================
    public synchronized Integer join(WebSocketSession session) {
        String matchKey = extractRoomCodeFromSession(session);
        int requiredPlayers = extractRequiredPlayersFromSession(session);
        int expectedHumanCount = extractHumanCountFromSession(session);
        int expectedBotCount = extractBotCountFromSession(session);
        String matchMode = extractMatchModeFromSession(session);

        // Nếu không có roomCode thì đây là quick play.
        // Tách queue SOLO / DUO để không bị trộn người chơi khác mode.
        if (matchKey == null || matchKey.isBlank()) {
            matchKey = buildQuickPlayMatchKey(matchMode);
        }

        // Quick play không dùng bot lobby.
        // Quick play luôn lấy default requiredPlayers và toàn bộ là người thật.
        if (matchKey.startsWith(QUICK_PLAY_KEY)) {
            requiredPlayers = gameConfig.getMatch().getDefaultRequiredPlayers();
            expectedHumanCount = requiredPlayers;
            expectedBotCount = 0;
        }

        // Validate requiredPlayers trong khoảng cho phép.
        if (requiredPlayers < gameConfig.getMatch().getMinRequiredPlayers()
                || requiredPlayers > gameConfig.getMatch().getMaxRequiredPlayers()) {
            requiredPlayers = gameConfig.getMatch().getDefaultRequiredPlayers();
        }

        // Bot count không được âm.
        if (expectedBotCount < 0) {
            expectedBotCount = 0;
        }

        // Bot count không được lớn hơn tổng số người yêu cầu.
        if (expectedBotCount > requiredPlayers) {
            expectedBotCount = requiredPlayers;
        }

        // Nếu lobby không truyền humanCount thì tự suy ra:
        // human = requiredPlayers - botCount
        if (expectedHumanCount <= 0) {
            expectedHumanCount = requiredPlayers - expectedBotCount;
        }

        // Ít nhất phải có 1 người thật.
        if (expectedHumanCount < 1) {
            expectedHumanCount = 1;
        }

        // Người thật không được vượt quá requiredPlayers.
        if (expectedHumanCount > requiredPlayers) {
            expectedHumanCount = requiredPlayers;
        }

        // Nếu human + bot > requiredPlayers thì cắt bot xuống.
        if (expectedHumanCount + expectedBotCount > requiredPlayers) {
            expectedBotCount = Math.max(0, requiredPlayers - expectedHumanCount);
        }

        final String finalMatchKey = matchKey;
        final int finalRequiredPlayers = requiredPlayers;
        final int finalExpectedHumanCount = expectedHumanCount;
        final int finalExpectedBotCount = expectedBotCount;
        final String finalMatchMode = sanitizeMatchMode(matchMode);

        // Lấy match đang có hoặc tạo mới nếu chưa tồn tại.
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

        // Nếu join vào match đã tồn tại thì vẫn cập nhật config
        // theo dữ liệu lobby / quick play mới nhất.
        match.expectedHumanCount = finalExpectedHumanCount;
        match.expectedBotCount = finalExpectedBotCount;
        match.matchMode = finalMatchMode;

        // Duyệt các slot player từ 1..requiredPlayers để tìm slot trống đầu tiên.
        for (int id = 1; id <= match.requiredPlayers; id++) {
            if (!isOccupiedSlot(match, id)) {
                // Tạo player mới với stat mặc định.
                Player fresh = createFreshPlayer(id);

                // Gán team theo match mode hiện tại.
                applyMatchModeToPlayer(match, fresh);

                // Đưa player vào danh sách player của match.
                match.players.put(id, fresh);

                // Gắn session websocket vào slot player này.
                match.sessions.put(id, session);

                // Lưu playerId và matchKey vào attributes session để tiện dùng lại.
                session.getAttributes().put("playerId", id);
                session.getAttributes().put("matchKey", finalMatchKey);

                // Nếu đây là custom room và chưa có host,
                // người vào đầu tiên sẽ là host của trận.
                if (match.customRoom && match.hostPlayerId == null) {
                    match.hostPlayerId = id;
                }

                // Gắn profile thật của user từ JWT vào player.
                attachProfileToPlayer(match, session, id);

                // Khi đủ người thật theo cấu hình lobby thì sync bot vào các slot trống.
                maybeSyncConfiguredBots(match);

                // Tính lại waiting / countdown.
                reevaluateWaitingState(match);
                return id;
            }
        }

        // Không tìm được slot trống => phòng đã đầy.
        return null;
    }

    // =========================================================
    // HÀM: leave
    // Mục đích:
    // - Xử lý khi người chơi rời khỏi trận
    //
    // Luồng xử lý:
    // 1. Xóa session khỏi match
    // 2. Đánh dấu player rời trận là chết / out map
    // 3. Nếu không còn session người thật nào:
    //    - finish room lobby nếu là custom room
    //    - xóa luôn match
    // 4. Nếu host rời thì chọn host mới
    // 5. Nếu trận chưa bắt đầu thì sync lại bot
    // 6. Tính lại trạng thái chờ / countdown
    // 7. Nếu đang chơi thì check thắng thua
    // 8. Broadcast state mới
    // =========================================================
    public synchronized void leave(String matchKey, int playerId) {
        MatchInstance match = matches.get(matchKey);
        if (match == null) return;

        // Xóa session websocket của người rời.
        match.sessions.remove(playerId);

        // Đánh dấu player rời khỏi trận là đã bị loại.
        Player leaving = match.players.get(playerId);
        if (leaving != null) {
            leaving.lives = 0;
            leaving.row = -99;
            leaving.col = -99;
            updateOvr(leaving);
        }

        // Nếu không còn session người thật nào,
        // dọn sạch match luôn.
        if (match.sessions.isEmpty()) {
            finishLobbyRoomIfNeeded(match);
            matches.remove(matchKey);
            return;
        }

        // Nếu host cũ rời thì tìm host người thật kế tiếp.
        if (Objects.equals(match.hostPlayerId, playerId)) {
            match.hostPlayerId = findNextHumanHost(match);
        }

        // Nếu người thật rời trước khi trận bắt đầu thì sync lại số bot.
        maybeSyncConfiguredBots(match);

        // Tính lại trạng thái chờ / countdown.
        reevaluateWaitingState(match);

        // Nếu game đã start rồi thì kiểm tra lại thắng thua.
        if (match.gameStarted) {
            checkWinner(match);
        }

        // Gửi trạng thái mới cho client.
        broadcastState(matchKey);
    }

    // =========================================================
    // HÀM: sendInit
    // Mục đích:
    // - Gửi playerId ban đầu về cho client
    //
    // Thường client dùng message này để biết mình là player nào
    // trong match hiện tại.
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
    //
    // Các loại message hỗ trợ:
    // - move
    // - bomb
    // - use_item
    // - skill_bomb
    // - skill_speed
    // - add_bot
    // - restart
    //
    // Sau khi xử lý xong sẽ broadcast state mới.
    // =========================================================
    public synchronized void handleClientMessage(String matchKey, int playerId, ClientMessage message) {
        MatchInstance match = matches.get(matchKey);
        if (match == null || message == null || message.type == null) return;

        switch (message.type) {
            case "move" -> {
                // Chỉ cho di chuyển khi game đã bắt đầu và chưa game over.
                if (!match.gameOver && match.gameStarted) {
                    handleMove(match, playerId, message.direction);
                }
            }

            case "bomb" -> {
                // Chỉ cho đặt bom khi game đã bắt đầu và chưa game over.
                if (!match.gameOver && match.gameStarted) {
                    handlePlaceBomb(match, playerId);
                }
            }

            case "use_item" -> {
                // Chỉ cho dùng item khi game đã bắt đầu và chưa game over.
                if (!match.gameOver && match.gameStarted) {
                    handleUseItem(match, playerId, message.slotIndex);
                }
            }

            case "skill_bomb" -> {
                // Skill tăng số bom tối đa.
                if (!match.gameOver && match.gameStarted) {
                    handleUseBombSkill(match, playerId);
                }
            }

            case "skill_speed" -> {
                // Skill tăng tốc tạm thời.
                if (!match.gameOver && match.gameStarted) {
                    handleUseSpeedSkill(match, playerId);
                }
            }

            case "add_bot" -> {
                // Chỉ cho add bot ở màn chờ trước khi game bắt đầu.
                if (!match.gameOver && !match.gameStarted) {
                    handleAddBot(match, playerId);
                }
            }

            // Restart phòng / trận.
            case "restart" -> handleRestart(match, playerId);

            default -> {
                // Bỏ qua các message type không hỗ trợ.
            }
        }

        // Luôn broadcast state sau khi xử lý message.
        broadcastState(matchKey);
    }

    // =========================================================
    // HÀM: createMatch
    // Mục đích:
    // - Tạo trạng thái nội bộ cho 1 match mới
    //
    // Dữ liệu khởi tạo:
    // - matchKey
    // - requiredPlayers
    // - expectedHumanCount
    // - expectedBotCount
    // - matchMode
    //
    // Sau đó gọi resetMatch để sinh map và reset toàn bộ state.
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

        // QUICK-PLAY-SOLO / QUICK-PLAY-DUO đều là quick play.
        // Nếu không bắt đầu bằng QUICK-PLAY thì coi là custom room.
        match.customRoom = !matchKey.startsWith(QUICK_PLAY_KEY);

        // Host chỉ dùng cho custom room.
        match.hostPlayerId = null;

        // Reset trạng thái game ban đầu.
        resetMatch(match);
        return match;
    }

    // =========================================================
    // HÀM: sanitizeMatchMode
    // Mục đích:
    // - Chuẩn hóa kiểu trận
    // - Chỉ cho phép SOLO hoặc DUO
    //
    // Nếu giá trị truyền vào không hợp lệ thì mặc định là SOLO.
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
    // - Tạo key quick play riêng cho từng mode
    //
    // Ví dụ:
    // - QUICK-PLAY-SOLO
    // - QUICK-PLAY-DUO
    // =========================================================
    private String buildQuickPlayMatchKey(String matchMode) {
        return QUICK_PLAY_KEY + "-" + sanitizeMatchMode(matchMode);
    }

    // =========================================================
    // HÀM: extractMatchModeFromSession
    // Mục đích:
    // - Lấy kiểu trận từ query param của websocket URL
    //
    // Thứ tự ưu tiên:
    // 1. queueMode  -> thường dùng cho quick play
    // 2. matchMode  -> thường dùng cho custom room
    // 3. mode       -> fallback cũ / legacy
    //
    // Cuối cùng luôn sanitize lại để chỉ ra SOLO hoặc DUO.
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
    // Quy ước:
    // - DUO:
    //   + Player 1 và Player 3 thuộc Đội Trái (LEFT)
    //   + Player 2 và Player 4 thuộc Đội Phải (RIGHT)
    //
    // - SOLO:
    //   + mỗi player là 1 team riêng, ví dụ P1, P2, P3...
    // =========================================================
    private void applyMatchModeToPlayer(MatchInstance match, Player player) {
        if (player == null) return;

        if (MATCH_MODE_DUO.equals(match.matchMode)) {
            player.teamId = (player.id == 1 || player.id == 3) ? "LEFT" : "RIGHT";
            return;
        }

        // SOLO: mỗi player là 1 team riêng.
        player.teamId = "P" + player.id;
    }

    // =========================================================
    // HÀM: isSameTeam
    // Mục đích:
    // - Kiểm tra 2 player có cùng team hay không
    // =========================================================
    private boolean isSameTeam(Player first, Player second) {
        if (first == null || second == null) return false;
        if (first.teamId == null || second.teamId == null) return false;
        return Objects.equals(first.teamId, second.teamId);
    }

    // =========================================================
    // HÀM: isBlockedFriendlyFire
    // Mục đích:
    // - Chặn friendly fire trong DUO
    //
    // Quy tắc:
    // - Đồng đội không gây damage / freeze cho nhau
    // - Nhưng bản thân vẫn có thể tự ăn bom của chính mình
    //
    // Trả về:
    // - true  = không cho gây hiệu ứng
    // - false = vẫn cho áp damage / freeze
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
    // - Đổi teamId sang text dễ đọc để hiển thị kết quả
    // =========================================================
    private String getTeamDisplayName(String teamId) {
        if ("LEFT".equals(teamId)) return "Đội Trái";
        if ("RIGHT".equals(teamId)) return "Đội Phải";
        return "Đội";
    }

    // =========================================================
    // HÀM: handleRestart
    // Mục đích:
    // - Reset lại toàn bộ trận
    // - Giữ nguyên cấu hình room hiện tại
    // - Sync lại bot theo số lượng đã cấu hình trước đó
    //
    // Điều kiện:
    // - Chỉ người có session thật trong phòng mới được restart
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
    // - Host của custom room thêm bot thủ công ở màn chờ
    //
    // Điều kiện:
    // - Phải là custom room
    // - Người gọi phải là host
    // - Game chưa bắt đầu
    // - Chưa đủ số participant
    //
    // Sau khi thêm:
    // - cập nhật expectedBotCount để restart không mất bot
    // - tính lại trạng thái waiting / countdown
    // =========================================================
    private void handleAddBot(MatchInstance match, int requesterPlayerId) {
        if (!match.customRoom) return;
        if (!Objects.equals(match.hostPlayerId, requesterPlayerId)) return;
        if (match.gameStarted) return;
        if (getParticipantCount(match) >= match.requiredPlayers) return;

        Integer freeSlot = findNextFreeSlot(match);
        if (freeSlot == null) return;

        Player bot = createFreshPlayer(freeSlot);

        // Gán team cho bot theo mode hiện tại.
        applyMatchModeToPlayer(match, bot);

        // Đánh dấu đây là bot.
        bot.bot = true;
        bot.ready = true;
        bot.displayName = "BOT_" + freeSlot;
        bot.characterName = "BOT_" + freeSlot;
        bot.gender = "male";
        bot.avatarCode = "bot";

        match.players.put(freeSlot, bot);

        // Cập nhật lại số bot kỳ vọng để khi restart vẫn giữ bot này.
        match.expectedBotCount = countCurrentBots(match);

        reevaluateWaitingState(match);
    }

    // =========================================================
    // HÀM: maybeSyncConfiguredBots
    // Mục đích:
    // - Đồng bộ bot trong custom room theo cấu hình lobby
    //
    // Logic:
    // 1. Nếu chưa đủ người thật => bỏ hết bot ra để chờ người thật
    // 2. Nếu số bot hiện tại > expectedBotCount => xóa bớt bot
    // 3. Nếu số bot hiện tại < expectedBotCount => thêm bot
    //
    // Giá trị trả về:
    // - true  = có thay đổi player/bot
    // - false = không đổi gì
    // =========================================================
    private boolean maybeSyncConfiguredBots(MatchInstance match) {
        if (!match.customRoom) {
            return false;
        }

        boolean changed = false;

        // Nếu game chưa start và chưa đủ người thật thì không giữ bot.
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

        // Nếu dư bot so với cấu hình thì xóa bớt.
        if (!match.gameStarted && currentBotCount > match.expectedBotCount) {
            List<Integer> botSlots = new ArrayList<>();

            for (Map.Entry<Integer, Player> entry : match.players.entrySet()) {
                Player p = entry.getValue();
                if (p != null && p.bot) {
                    botSlots.add(entry.getKey());
                }
            }

            // Xóa từ slot lớn xuống để ổn định slot nhỏ trước.
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

        // Nếu thiếu bot thì thêm tiếp vào slot trống.
        while (currentBotCount < match.expectedBotCount) {
            Integer freeSlot = findNextFreeSlot(match);
            if (freeSlot == null) break;

            Player bot = createFreshPlayer(freeSlot);

            // Gán team cho bot theo match mode hiện tại.
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
    // - Tìm slot player đầu tiên còn trống
    //
    // Slot trống là slot không có:
    // - session người thật
    // - hoặc bot
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
    // - Tìm host người thật kế tiếp khi host cũ rời trận
    //
    // Cách chọn:
    // - lấy playerId nhỏ nhất trong danh sách session đang còn online
    // =========================================================
    private Integer findNextHumanHost(MatchInstance match) {
        return match.sessions.keySet().stream().sorted().findFirst().orElse(null);
    }

    // =========================================================
    // HÀM: isOccupiedSlot
    // Mục đích:
    // - Kiểm tra 1 slot có đang bị chiếm hay không
    //
    // Một slot được xem là bị chiếm nếu:
    // - có session người thật
    // - hoặc có bot trong slot đó
    // =========================================================
    private boolean isOccupiedSlot(MatchInstance match, int playerId) {
        return match.sessions.containsKey(playerId) || isBotSlot(match, playerId);
    }

    // =========================================================
    // HÀM: isBotSlot
    // Mục đích:
    // - Kiểm tra slot có phải bot hay không
    // =========================================================
    private boolean isBotSlot(MatchInstance match, int playerId) {
        Player player = match.players.get(playerId);
        return player != null && player.bot;
    }

    // =========================================================
    // HÀM: canControlPlayer
    // Mục đích:
    // - Kiểm tra player có thể điều khiển được không
    //
    // Áp dụng cho:
    // - người thật có session
    // - bot đang tồn tại trong room
    // =========================================================
    private boolean canControlPlayer(MatchInstance match, int playerId) {
        return match.sessions.containsKey(playerId) || isBotSlot(match, playerId);
    }

    // =========================================================
    // HÀM: isActiveParticipant
    // Mục đích:
    // - Kiểm tra player có còn là participant hợp lệ không
    //
    // Điều kiện active:
    // - player không null
    // - và là người thật còn session hoặc là bot
    // =========================================================
    private boolean isActiveParticipant(MatchInstance match, Player player) {
        return player != null && (match.sessions.containsKey(player.id) || player.bot);
    }

    // =========================================================
    // HÀM: getParticipantCount
    // Mục đích:
    // - Đếm tổng số participant hiện tại trong trận
    //
    // Participant bao gồm:
    // - người thật
    // - bot
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
    // - Lấy danh sách player đang active trong trận
    //
    // Bao gồm:
    // - người thật còn session
    // - bot
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
    // - Tạo player mới với stat mặc định lúc bắt đầu trận
    //
    // Giá trị khởi tạo:
    // - vị trí spawn theo playerId
    // - hướng mặc định down
    // - lives, bombs, range, speed từ config
    // - tên mặc định Player-x
    // - team mặc định P{id}
    // - inventory rỗng
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
        // Khi join / reset sẽ được apply lại theo mode thực tế.
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
    //
    // Luồng:
    // 1. Lấy token từ query param
    // 2. Validate token
    // 3. Đọc userId từ token
    // 4. Tìm profile theo userId
    // 5. Gán characterName / gender / avatarCode cho player
    //
    // Nếu có lỗi thì bỏ qua nhẹ nhàng, không làm crash trận.
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
            // Cố tình bỏ qua lỗi để tránh ngắt luồng game.
        }
    }

    // =========================================================
    // HÀM: extractTokenFromSession
    // Mục đích:
    // - Lấy JWT token từ query param websocket
    // =========================================================
    private String extractTokenFromSession(WebSocketSession session) {
        return extractQueryParam(session, "token");
    }

    // =========================================================
    // HÀM: extractRoomCodeFromSession
    // Mục đích:
    // - Lấy roomCode từ query param websocket
    //
    // Nếu có giá trị thì:
    // - trim
    // - uppercase để đồng nhất key
    // =========================================================
    private String extractRoomCodeFromSession(WebSocketSession session) {
        String value = extractQueryParam(session, "roomCode");
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    // =========================================================
    // HÀM: extractRequiredPlayersFromSession
    // Mục đích:
    // - Lấy requiredPlayers từ query param websocket
    //
    // Nếu không có hoặc parse lỗi thì trả về defaultRequiredPlayers.
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
    // - Lấy humanCount từ query param websocket
    //
    // Nếu không có hoặc parse lỗi thì trả về 0.
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
    // - Lấy botCount từ query param websocket
    //
    // Nếu không có hoặc parse lỗi thì trả về 0.
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
    //
    // Ví dụ URL:
    // ws://host/ws/game?token=abc&roomCode=R1&requiredPlayers=4
    //
    // Gọi:
    // extractQueryParam(session, "roomCode") -> R1
    //
    // Nếu không có hoặc có lỗi thì trả về null.
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
            // Bỏ qua lỗi parse query để tránh crash websocket.
        }
        return null;
    }

    // =========================================================
    // HÀM: reevaluateWaitingState
    // Mục đích:
    // - Tính lại trạng thái waiting / countdown trước khi trận bắt đầu
    //
    // Logic:
    // - Nếu game đã start thì không làm gì
    // - Nếu participant >= requiredPlayers:
    //     + waiting = false
    //     + start countdown nếu chưa chạy
    // - Nếu participant < requiredPlayers:
    //     + waiting = true
    //     + hủy countdown
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
    //
    // Các thay đổi:
    // - gameStarted = true
    // - waitingForPlayers = false
    // - countdown = 0
    // - set thời gian bắt đầu trận
    // - reset cờ historySaved
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
    // - Lấy thời gian delay giữa 2 lần di chuyển của player
    // - Tính theo speedLevel
    //
    // speedLevel càng cao thì cooldown càng thấp.
    // =========================================================
    private long getMoveCooldown(Player player) {
        return gameConfig.getMoveCooldownForSpeedLevel(player.speedLevel);
    }

    // =========================================================
    // HÀM: updateOvr
    // Mục đích:
    // - Tính lại OVR cho 1 player
    //
    // Công thức hiện tại:
    // - kill * 100
    // - lives * 50
    // - bombsPlaced * 10
    // - deaths * -20
    //
    // Kết quả không âm.
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
    // - Tính lại OVR cho toàn bộ player trong trận
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
    // - Kiểm tra player có đang bị đóng băng hay không
    //
    // Điều kiện frozen:
    // - frozenUntil > 0
    // - thời gian hiện tại vẫn nhỏ hơn frozenUntil
    // =========================================================
    private boolean isPlayerFrozen(Player player, long now) {
        return player.frozenUntil > 0 && now < player.frozenUntil;
    }

    // =========================================================
    // HÀM: handleMove (String)
    // Mục đích:
    // - Parse direction từ chuỗi string
    // - Sau đó gọi sang handleMove(Direction)
    //
    // Nếu direction không hợp lệ thì bỏ qua.
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
    //
    // Kiểm tra:
    // - player tồn tại
    // - player có quyền điều khiển
    // - player còn sống
    // - không bị frozen
    // - chưa vi phạm cooldown di chuyển
    // - ô tiếp theo đi được
    //
    // Nếu đi được:
    // - cập nhật row/col
    // - nhặt item nếu có
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
    //
    // Điều kiện:
    // - player tồn tại, còn sống, điều khiển được
    // - số bom đang active của player < maxBombs
    // - ô hiện tại chưa có bom khác
    //
    // Khi đặt bom:
    // - lấy cờ randomPattern / freezeEffect từ player
    // - add bomb vào danh sách match.bombs
    // - reset cờ nextBombRandom / nextBombFreeze
    // - tăng bombsPlaced
    // - cập nhật OVR
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

        // Sau khi dùng cho 1 quả bom thì reset cờ hiệu ứng đặc biệt.
        player.nextBombRandom = false;
        player.nextBombFreeze = false;

        player.bombsPlaced += 1;
        updateOvr(player);
    }

    // =========================================================
    // HÀM: handleUseBombSkill
    // Mục đích:
    // - Skill tăng số lượng bom tối đa mà player được đặt
    //
    // Logic:
    // - maxBombs += 1
    // - không vượt quá gameConfig.player.maxBombs
    //
    // Áp dụng cho cả người thật và bot.
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
    //
    // Logic:
    // - boostedSpeed = baseSpeedLevel + 2
    // - không vượt maxSpeedLevel
    // - gán speedBoostUntil = now + 5s
    //
    // Khi hết thời gian, tick() sẽ tự trả speedLevel về baseSpeedLevel.
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
    // - Dùng item trong inventory theo slotIndex
    //
    // Các item hỗ trợ:
    // - BOMB_UP     : tăng maxBombs
    // - FLAME_UP    : tăng bombRange
    // - SPEED_UP    : tăng baseSpeedLevel
    // - SHIELD      : tăng invulnerableUntil
    // - HEART       : tăng lives
    // - TELEPORT    : dịch chuyển ngẫu nhiên tới ô an toàn
    // - RANDOM_BOMB : bom kế tiếp nổ pattern ngẫu nhiên
    // - FREEZE_BOMB : bom kế tiếp có hiệu ứng đóng băng
    //
    // Sau khi dùng item:
    // - item bị remove khỏi inventory
    // - cập nhật OVR
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
                // SPEED_UP làm tăng baseSpeedLevel vĩnh viễn.
                player.baseSpeedLevel = Math.min(
                        player.baseSpeedLevel + 1,
                        gameConfig.getPlayer().getMaxSpeedLevel()
                );

                // Nếu hiện tại không có buff speed tạm thời,
                // thì speedLevel đi theo baseSpeedLevel ngay.
                if (player.speedBoostUntil <= now) {
                    player.speedLevel = player.baseSpeedLevel;
                } else {
                    // Nếu đang có buff speed thì đảm bảo speed hiện tại
                    // không bị thấp hơn base mới.
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
    //
    // Mỗi tick sẽ xử lý:
    // 1. Countdown trước khi start
    // 2. Hết hiệu ứng speed skill
    // 3. Hết đóng băng
    // 4. Update bot
    // 5. Bom nổ
    // 6. Xóa explosion hết hạn
    // 7. Áp damage / freeze
    // 8. Broadcast state nếu có thay đổi
    //
    // Chạy theo chu kỳ:
    // - game.match.tick-rate-ms
    // =========================================================
    @Scheduled(fixedRateString = "${game.match.tick-rate-ms:100}")
    public synchronized void tick() {
        List<String> matchKeys = new ArrayList<>(matches.keySet());

        for (String matchKey : matchKeys) {
            MatchInstance match = matches.get(matchKey);
            if (match == null) continue;

            boolean changed = false;
            long now = System.currentTimeMillis();

            // Countdown trước khi bắt đầu trận.
            if (!match.gameStarted && match.countdownStartedAt != null) {
                long elapsed = now - match.countdownStartedAt;
                int remain = gameConfig.getMatch().getStartCountdownSeconds() - (int) (elapsed / 1000);

                if (remain < 0) remain = 0;

                // Nếu số giây countdown thay đổi thì đánh dấu changed.
                if (!Objects.equals(match.countdownSeconds, remain)) {
                    match.countdownSeconds = remain;
                    changed = true;
                }

                // Đủ thời gian countdown thì start trận ngay.
                if (elapsed >= gameConfig.getMatch().getStartCountdownSeconds() * 1000L) {
                    startMatchNow(match);
                    changed = true;
                }
            }

            // Nếu game chưa bắt đầu thì chỉ cần broadcast thay đổi countdown/waiting.
            if (!match.gameStarted) {
                if (changed) {
                    broadcastState(matchKey);
                }
                continue;
            }

            boolean gameChanged = false;

            // Hết hiệu ứng speed skill.
            for (Player player : match.players.values()) {
                if (player == null) continue;

                if (player.speedBoostUntil > 0 && now >= player.speedBoostUntil) {
                    player.speedBoostUntil = 0L;
                    player.speedLevel = player.baseSpeedLevel;
                    gameChanged = true;
                }
            }

            // Hết đóng băng.
            for (Player player : match.players.values()) {
                if (player == null) continue;

                if (player.frozenUntil > 0 && now >= player.frozenUntil) {
                    player.frozenUntil = 0L;
                    gameChanged = true;
                }
            }

            // Tick quyết định của bot.
            if (updateBots(match, now)) {
                gameChanged = true;
            }

            // Tìm các bom đã hết fuse để nổ.
            List<Bomb> expired = match.bombs.stream()
                    .filter(b -> now - b.placedAt >= gameConfig.getTiming().getBombFuseMs())
                    .toList();

            if (!expired.isEmpty()) {
                detonateBombs(match, expired, now);
                gameChanged = true;
            }

            // Xóa các explosion đã hết thời gian hiển thị.
            int beforeExplosions = match.explosions.size();
            match.explosions.removeIf(e -> now - e.startedAt >= e.duration);
            if (beforeExplosions != match.explosions.size()) {
                gameChanged = true;
            }

            // Áp damage / freeze nếu player đang đứng trong vùng nổ.
            if (applyDamageOrFreeze(match, now)) {
                gameChanged = true;
            }

            // Chỉ broadcast nếu có thay đổi.
            if (changed || gameChanged) {
                broadcastState(matchKey);
            }
        }
    }

    // =========================================================
    // HÀM: updateBots
    // Mục đích:
    // - Tính quyết định của bot ở mỗi tick
    //
    // Luồng bot:
    // 1. decide() từ BotService
    // 2. dùng skill bomb nếu cần
    // 3. dùng skill speed nếu cần
    // 4. dùng item nếu cần
    // 5. đặt bom nếu cần
    // 6. di chuyển nếu cần
    //
    // Giá trị trả về:
    // - true  = có thay đổi trạng thái game
    // - false = không có thay đổi
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

            // 1) Dùng skill tăng bom.
            if (decision.useSkillBomb()) {
                int before = bot.maxBombs;
                handleUseBombSkill(match, bot.id);
                if (bot.maxBombs != before) {
                    changed = true;
                }
            }

            // 2) Dùng skill tăng tốc.
            if (decision.useSkillSpeed()) {
                int beforeSpeed = bot.speedLevel;
                long beforeUntil = bot.speedBoostUntil;
                handleUseSpeedSkill(match, bot.id);
                if (bot.speedLevel != beforeSpeed || bot.speedBoostUntil != beforeUntil) {
                    changed = true;
                }
            }

            // 3) Dùng item.
            if (decision.useItemSlot() != null) {
                int beforeInv = bot.inventory.size();
                handleUseItem(match, bot.id, decision.useItemSlot());
                if (bot.inventory.size() != beforeInv) {
                    changed = true;
                }
            }

            // 4) Đặt bom.
            if (decision.placeBomb()) {
                int beforeBombs = match.bombs.size();
                handlePlaceBomb(match, bot.id);
                if (match.bombs.size() != beforeBombs) {
                    changed = true;

                    // Vừa đặt bom xong thì reset think time
                    // để bot lập tức chuyển sang mode chạy thoát.
                    bot.botNextThinkAt = 0L;
                }
            }

            // 5) Di chuyển.
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
    // - Xử lý bom nổ
    //
    // Bao gồm:
    // - tạo các ô flame
    // - phá tường mềm
    // - drop item
    // - tạo explosion
    // - gây nổ dây chuyền cho bom khác nếu flame chạm vào
    //
    // Luồng:
    // - Dùng queue để xử lý chain explosion
    // - Dùng set detonatedIds để tránh nổ lặp cùng 1 bom
    // =========================================================
    private void detonateBombs(MatchInstance match, List<Bomb> expired, long now) {
        Queue<Bomb> queue = new ArrayDeque<>(expired);
        Set<String> detonatedIds = new HashSet<>();

        while (!queue.isEmpty()) {
            Bomb bomb = queue.poll();
            if (!detonatedIds.add(bomb.id)) continue;

            // Tính vùng nổ của quả bom hiện tại.
            List<FlameCell> cells = buildExplosionCells(match, bomb);

            // Nếu flame chạm tường mềm thì phá tường và có thể rơi item.
            for (FlameCell cell : cells) {
                if (match.board[cell.row][cell.col] == 2) {
                    match.board[cell.row][cell.col] = 0;
                    maybeDropItem(match, cell.row, cell.col);
                }
            }

            // Tạo explosion để client render flame.
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

            // Lưu set ô flame để kiểm tra chain nổ với bom khác.
            Set<String> flameSet = new HashSet<>();
            for (FlameCell cell : cells) {
                flameSet.add(cell.row + ":" + cell.col);
            }

            // Nếu flame chạm ô có bom khác thì bom đó nổ dây chuyền.
            for (Bomb other : match.bombs) {
                if (detonatedIds.contains(other.id)) continue;
                if (flameSet.contains(other.row + ":" + other.col)) {
                    queue.offer(other);
                }
            }
        }

        // Sau khi xử lý xong thì remove toàn bộ bom đã nổ.
        match.bombs.removeIf(b -> detonatedIds.contains(b.id));
    }

    // =========================================================
    // HÀM: applyDamageOrFreeze
    // Mục đích:
    // - Áp damage hoặc freeze khi player đứng trong vùng explosion
    //
    // Logic:
    // - Nếu gặp normal bomb:
    //     + trừ 1 mạng
    //     + tăng deaths
    //     + set invulnerableUntil
    //     + cộng kills cho killer (nếu không tự giết)
    //     + respawn nếu còn mạng, ngược lại out map
    //
    // - Nếu gặp freeze bomb:
    //     + set frozenUntil
    //
    // DUO:
    // - đồng đội không gây damage / freeze cho nhau
    //
    // Giá trị trả về:
    // - true nếu có thay đổi trạng thái player
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

                    // Ở DUO: chặn đồng đội gây damage / freeze cho nhau.
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

            // Trúng bom thường => mất mạng.
            if (hitNormalBomb) {
                victim.lives -= 1;
                victim.deaths += 1;
                victim.invulnerableUntil = now + gameConfig.getTiming().getInvulnerableMs();
                updateOvr(victim);

                // Cộng kill cho người đặt bom nếu không phải tự sát.
                if (killerId != null && killerId != victim.id) {
                    Player killer = match.players.get(killerId);
                    if (killer != null) {
                        killer.kills += 1;
                        updateOvr(killer);
                    }
                }

                // Nếu còn mạng thì hồi sinh, không thì out khỏi map.
                if (victim.lives > 0) {
                    respawn(victim);
                } else {
                    victim.row = -99;
                    victim.col = -99;
                }

                changed = true;
                continue;
            }

            // Trúng freeze bomb => đóng băng trong 1 khoảng thời gian.
            victim.frozenUntil = Math.max(
                    victim.frozenUntil,
                    now + gameConfig.getTiming().getFreezeDurationMs()
            );
            changed = true;
        }

        // Nếu có thay đổi trạng thái thì kiểm tra thắng thua.
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
    // - còn 1 player sống cuối cùng => player đó thắng
    //
    // DUO:
    // - còn 1 team sống cuối cùng => team đó thắng
    //
    // Nếu không còn ai sống:
    // - hòa
    //
    // Khi có kết quả:
    // - gameOver = true
    // - set winnerId / resultMessage
    // - updateAllOvr
    // - save history
    // - finish room lobby nếu cần
    // =========================================================
    private void checkWinner(MatchInstance match) {
        if (match.gameOver) return;
        if (!match.gameStarted) return;

        // Lấy toàn bộ player còn sống.
        List<Player> alivePlayers = match.players.values().stream()
                .filter(p -> isActiveParticipant(match, p))
                .filter(p -> p.lives > 0)
                .toList();

        // Gom nhóm player còn sống theo teamId.
        Map<String, List<Player>> aliveTeams = new LinkedHashMap<>();
        for (Player alive : alivePlayers) {
            String teamId = alive.teamId == null || alive.teamId.isBlank()
                    ? "P" + alive.id
                    : alive.teamId;
            aliveTeams.computeIfAbsent(teamId, key -> new ArrayList<>()).add(alive);
        }

        // Nếu chỉ còn 1 team sống => team đó thắng.
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

        // Nếu không còn ai sống => hòa.
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
    //
    // Dùng khi:
    // - trận kết thúc
    // - hoặc mọi người đã rời trận
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
    // Chỉ lưu khi:
    // - gameOver = true
    // - historySaved = false
    //
    // Dữ liệu lưu:
    // - roomCode
    // - startedAt / endedAt
    // - winnerUserId / winnerCharacterName
    // - participantUserIds
    // - kết quả từng player
    //
    // Lưu ý DUO:
    // - winnerId chỉ là 1 người đại diện team thắng
    // - player nào cùng team với winner sẽ có result.winner = true
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
            // In lỗi để debug nếu save history thất bại.
            e.printStackTrace();
        }
    }

    // =========================================================
    // HÀM: respawn
    // Mục đích:
    // - Hồi sinh player về vị trí spawn ban đầu
    //
    // Reset:
    // - row / col / direction
    // - speed buff tạm thời
    // - frozen
    // - cờ bomb đặc biệt
    //
    // Không reset:
    // - lives hiện tại
    // - maxBombs
    // - bombRange
    // - baseSpeedLevel
    // - inventory
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
    // - Nhặt item nếu player đang đứng trên ô có item
    //
    // Điều kiện:
    // - inventory chưa đầy
    //
    // Khi nhặt:
    // - add item.type vào inventory
    // - remove item khỏi map
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
    //
    // Điều kiện:
    // - ô đó chưa có item
    // - random <= itemRate
    //
    // Pool item hiện tại:
    // - BOMB_UP
    // - FLAME_UP
    // - SPEED_UP
    // - SHIELD
    // - HEART
    // - TELEPORT
    // - RANDOM_BOMB
    // - FREEZE_BOMB
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
    //
    // Điều kiện ô đi được:
    // - nằm trong map
    // - không phải hard wall / soft wall
    // - không có bom
    // - không có player khác đang đứng
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
    // - Dịch chuyển player đến 1 ô an toàn ngẫu nhiên
    //
    // Cách làm:
    // - Quét toàn map
    // - Lấy tất cả ô hợp lệ có thể teleport tới
    // - Loại ô hiện tại của player
    // - Random 1 ô trong danh sách đó
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
    //
    // Điều kiện:
    // - nằm trong map
    // - ô trống
    // - không có bom
    // - không có player khác đang đứng
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
    // - Chọn cách tạo vùng nổ của bom
    //
    // Nếu bomb.randomPattern = true:
    // - dùng pattern nổ ngẫu nhiên
    //
    // Ngược lại:
    // - dùng pattern nổ chuẩn 4 hướng
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
    //
    // Quy tắc:
    // - có tâm ở ô bom
    // - lan theo 4 hướng trái/phải/lên/xuống
    // - dừng khi gặp hard wall
    // - nếu gặp soft wall thì ô đó vẫn cháy nhưng dừng tiếp
    // - ô cuối mỗi nhánh dùng kind là end để client render đầu lửa
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
    //
    // Cách làm:
    // - bắt đầu từ ô tâm
    // - giữ 1 danh sách frontier các ô đã đi được
    // - mỗi bước chọn ngẫu nhiên 1 ô gốc và 1 hướng
    // - nếu ô mới hợp lệ thì thêm vào flame
    // - nếu không phải soft wall thì thêm vào frontier để lan tiếp
    //
    // stepCount:
    // - tối thiểu 4
    // - hoặc range * 4
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

            // Nếu không phải soft wall thì cho ô này tiếp tục lan nhánh.
            if (match.board[nr][nc] != 2) {
                frontier.add(new int[]{nr, nc});
            }
        }

        return cells;
    }

    // =========================================================
    // HÀM: inBounds
    // Mục đích:
    // - Kiểm tra ô có nằm trong biên map hay không
    // =========================================================
    private boolean inBounds(int row, int col) {
        return row >= 0 && row < gameConfig.getBoard().getRows()
                && col >= 0 && col < gameConfig.getBoard().getCols();
    }

    // =========================================================
    // HÀM: createInitialBoard
    // Mục đích:
    // - Tạo map ban đầu
    //
    // Quy tắc:
    // - biên ngoài là hard wall
    // - các ô chẵn/chẵn bên trong là hard wall
    // - các ô còn lại là ground
    // - sau đó random soft wall theo tỉ lệ config
    // - giữ vùng spawn của 4 người chơi luôn an toàn
    //
    // Mã tile:
    // - 0 = ground
    // - 1 = hard wall
    // - 2 = soft wall
    // =========================================================
    private int[][] createInitialBoard() {
        int rows = gameConfig.getBoard().getRows();
        int cols = gameConfig.getBoard().getCols();
        int[][] newBoard = new int[rows][cols];

        // Sinh khung map với hard wall cố định.
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

        // Tập ô an toàn quanh spawn để không bị sinh soft wall chặn ngay đầu game.
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

        // Random soft wall cho các ô ground không nằm trong vùng safe.
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
    //
    // Việc làm:
    // - clear toàn bộ match.players
    // - tạo fresh player cho từng slot từ 1..requiredPlayers
    // - apply team theo mode hiện tại
    // - attach profile lại cho các slot có session thật
    // - update OVR toàn bộ
    //
    // Lưu ý:
    // - hàm này reset đúng số slot của trận hiện tại
    // =========================================================
    private void resetPlayersOnly(MatchInstance match) {
        match.players.clear();

        for (int id = 1; id <= match.requiredPlayers; id++) {
            Player freshPlayer = createFreshPlayer(id);
            applyMatchModeToPlayer(match, freshPlayer);
            match.players.put(id, freshPlayer);
        }

        // Gắn lại profile thật cho những slot hiện đang có session.
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
    // - Reset toàn bộ trận về trạng thái ban đầu
    //
    // Bao gồm:
    // - tạo lại board
    // - clear bombs / explosions / items / lastMoveAt
    // - reset gameOver / winner / resultMessage
    // - reset gameStarted / waiting / countdown
    // - reset thời gian bắt đầu trận / historySaved
    // - reset toàn bộ player
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
    //
    // Bước làm:
    // 1. Tạo GameState copy an toàn từ dữ liệu nội bộ
    // 2. Convert sang JSON
    // 3. Gửi đến toàn bộ WebSocketSession còn mở
    //
    // Chỉ gửi cho người thật có session.
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
                // Bỏ qua lỗi gửi cho từng session để không ảnh hưởng session khác.
            }
        }
    }

    // =========================================================
    // HÀM: send
    // Mục đích:
    // - Gửi 1 websocket message đến 1 session duy nhất
    //
    // Dùng cho:
    // - init
    // - error
    // - các message lẻ khác nếu cần
    // =========================================================
    private void send(WebSocketSession session, ServerMessage message) {
        try {
            if (session != null && session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            }
        } catch (IOException ignored) {
            // Bỏ qua lỗi gửi để không làm crash luồng server.
        }
    }

    // =========================================================
    // HÀM: copyBoard
    // Mục đích:
    // - Copy board để gửi ra ngoài
    //
    // Tránh trả trực tiếp reference board nội bộ của match.
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
    //
    // Chỉ copy các player active:
    // - người thật còn session
    // - bot
    //
    // Sau khi copy xong sẽ sort theo playerId.
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
    //
    // Tránh lộ trực tiếp reference nội bộ.
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
    //
    // Copy cả danh sách FlameCell bên trong mỗi explosion.
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
    // - Lưu toàn bộ trạng thái nội bộ của 1 trận
    //
    // Bao gồm:
    // - thông tin room / mode / host
    // - session người thật
    // - player / board / bombs / explosions / items
    // - trạng thái start / game over / countdown
    // - dữ liệu phục vụ lưu lịch sử
    // =========================================================
    private static class MatchInstance {

        /**
         * Key định danh trận.
         * Có thể là:
         * - QUICK-PLAY-SOLO
         * - QUICK-PLAY-DUO
         * - ROOMCODE custom
         */
        String matchKey;

        /**
         * Tổng số slot participant của trận.
         */
        int requiredPlayers;

        /**
         * Số người thật mong muốn từ lobby truyền qua.
         */
        int expectedHumanCount;

        /**
         * Số bot mong muốn từ lobby truyền qua.
         */
        int expectedBotCount;

        /**
         * true  = custom room
         * false = quick play
         */
        boolean customRoom;

        /**
         * PlayerId của host trong custom room.
         */
        Integer hostPlayerId;

        /**
         * Match mode hiện tại:
         * - SOLO
         * - DUO
         */
        String matchMode = MATCH_MODE_SOLO;

        /**
         * Danh sách session người thật đang online trong match.
         * key = playerId
         */
        Map<Integer, WebSocketSession> sessions = new ConcurrentHashMap<>();

        /**
         * Danh sách player trong match.
         * Bao gồm cả người thật lẫn bot.
         * key = playerId
         */
        Map<Integer, Player> players = new HashMap<>();

        /**
         * Lưu timestamp lần move gần nhất của từng player
         * để chống di chuyển quá nhanh.
         */
        Map<Integer, Long> lastMoveAt = new HashMap<>();

        /**
         * Board hiện tại của trận.
         */
        int[][] board;

        /**
         * Danh sách bom đang còn active.
         */
        List<Bomb> bombs = new ArrayList<>();

        /**
         * Danh sách explosion đang hiển thị / còn hiệu lực.
         */
        List<Explosion> explosions = new ArrayList<>();

        /**
         * Danh sách item hiện có trên map.
         */
        List<Item> items = new ArrayList<>();

        /**
         * Cờ kết thúc trận.
         */
        boolean gameOver = false;

        /**
         * PlayerId đại diện người thắng.
         * Ở DUO đây chỉ là 1 người đại diện của team thắng.
         */
        Integer winnerId = null;

        /**
         * Chuỗi mô tả kết quả trận.
         * Ví dụ:
         * - "Player A thắng trận"
         * - "Đội Trái thắng trận"
         * - "Hòa - không còn người sống"
         */
        String resultMessage = null;

        /**
         * true nếu trận đã bắt đầu.
         */
        boolean gameStarted = false;

        /**
         * true nếu đang chờ đủ người chơi.
         */
        boolean waitingForPlayers = true;

        /**
         * Số giây countdown còn lại trước khi start.
         */
        Integer countdownSeconds = null;

        /**
         * Thời điểm bắt đầu countdown.
         */
        Long countdownStartedAt = null;

        /**
         * Thời điểm trận chính thức bắt đầu.
         * Dùng để lưu history.
         */
        Instant matchStartedAt = null;

        /**
         * Đánh dấu history của trận này đã được lưu hay chưa.
         * Dùng để tránh lưu trùng.
         */
        boolean historySaved = false;
    }
}
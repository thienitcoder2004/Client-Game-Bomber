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
    // QUICK_PLAY = chơi nhanh, không dùng room lobby riêng
    // =========================================================
    private static final String QUICK_PLAY_KEY = "QUICK-PLAY";
    private static final long SPEED_SKILL_DURATION_MS = 5000L;

    private final ObjectMapper objectMapper;
    private final GameConfigProperties gameConfig;
    private final Random random = new Random();
    private final JwtService jwtService;
    private final CharacterProfileRepository characterProfileRepository;
    private final HistoryService historyService;
    private final BotAiService botAiService;

    // matchKey -> trận hiện tại
    private final Map<String, MatchInstance> matches = new ConcurrentHashMap<>();

    public GameRoomService(
            ObjectMapper objectMapper,
            GameConfigProperties gameConfig,
            JwtService jwtService,
            CharacterProfileRepository characterProfileRepository,
            HistoryService historyService,
            BotAiService botAiService
    ) {
        this.objectMapper = objectMapper;
        this.gameConfig = gameConfig;
        this.jwtService = jwtService;
        this.characterProfileRepository = characterProfileRepository;
        this.historyService = historyService;
        this.botAiService = botAiService;
    }

    // =========================================================
    // Người chơi kết nối vào trận
    //
    // Ý tưởng:
    // - room lobby sẽ truyền sang:
    //   + roomCode
    //   + requiredPlayers
    //   + humanCount
    //   + botCount
    //
    // GameRoomService sẽ:
    // - cho người thật vào trước
    // - khi đủ số người thật thì mới tự sync bot vào slot trống
    // =========================================================
    public synchronized Integer join(WebSocketSession session) {
        String matchKey = extractRoomCodeFromSession(session);
        int requiredPlayers = extractRequiredPlayersFromSession(session);
        int expectedHumanCount = extractHumanCountFromSession(session);
        int expectedBotCount = extractBotCountFromSession(session);

        if (matchKey == null || matchKey.isBlank()) {
            matchKey = QUICK_PLAY_KEY;
        }

        // Quick play không dùng bot lobby
        if (QUICK_PLAY_KEY.equals(matchKey)) {
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

        // Nếu room lobby không truyền humanCount thì tự suy ra
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

        MatchInstance match = matches.computeIfAbsent(
                finalMatchKey,
                key -> createMatch(
                        key,
                        finalRequiredPlayers,
                        finalExpectedHumanCount,
                        finalExpectedBotCount
                )
        );

        // Join sau vẫn cập nhật lại config từ room lobby
        match.expectedHumanCount = finalExpectedHumanCount;
        match.expectedBotCount = finalExpectedBotCount;

        for (int id = 1; id <= match.requiredPlayers; id++) {
            if (!isOccupiedSlot(match, id)) {
                Player fresh = createFreshPlayer(id);
                match.players.put(id, fresh);

                match.sessions.put(id, session);
                session.getAttributes().put("playerId", id);
                session.getAttributes().put("matchKey", finalMatchKey);

                if (match.customRoom && match.hostPlayerId == null) {
                    match.hostPlayerId = id;
                }

                attachProfileToPlayer(match, session, id);

                // Đủ số người thật rồi thì mới sync bot vào
                maybeSyncConfiguredBots(match);

                reevaluateWaitingState(match);
                return id;
            }
        }

        return null;
    }

    // =========================================================
    // Người chơi rời trận
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

        if (match.sessions.isEmpty()) {
            matches.remove(matchKey);
            return;
        }

        if (Objects.equals(match.hostPlayerId, playerId)) {
            match.hostPlayerId = findNextHumanHost(match);
        }

        // Nếu người thật rời trước lúc bắt đầu thì bot phải sync lại
        maybeSyncConfiguredBots(match);

        reevaluateWaitingState(match);

        if (match.gameStarted) {
            checkWinner(match);
        }

        broadcastState(matchKey);
    }

    public synchronized void sendInit(WebSocketSession session, int playerId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("playerId", playerId);
        send(session, new ServerMessage("init", payload));
    }

    // =========================================================
    // Nhận message từ client
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

            // Nút thêm bot trong màn chờ game
            case "add_bot" -> {
                if (!match.gameOver && !match.gameStarted) {
                    handleAddBot(match, playerId);
                }
            }

            case "restart" -> handleRestart(match, playerId);

            default -> {
            }
        }

        broadcastState(matchKey);
    }

    // =========================================================
    // Tạo match mới
    // =========================================================
    private MatchInstance createMatch(
            String matchKey,
            int requiredPlayers,
            int expectedHumanCount,
            int expectedBotCount
    ) {
        MatchInstance match = new MatchInstance();
        match.matchKey = matchKey;
        match.requiredPlayers = requiredPlayers;
        match.expectedHumanCount = expectedHumanCount;
        match.expectedBotCount = expectedBotCount;
        match.customRoom = !QUICK_PLAY_KEY.equals(matchKey);
        match.hostPlayerId = null;
        resetMatch(match);
        return match;
    }

    // =========================================================
    // Restart trận:
    // - reset map
    // - giữ lại config room lobby
    // - sync bot lại
    // =========================================================
    private void handleRestart(MatchInstance match, int playerId) {
        if (!match.sessions.containsKey(playerId)) return;

        resetMatch(match);
        maybeSyncConfiguredBots(match);
        reevaluateWaitingState(match);
    }

    // =========================================================
    // Host thêm bot trực tiếp ở màn chờ game
    // =========================================================
    private void handleAddBot(MatchInstance match, int requesterPlayerId) {
        if (!match.customRoom) return;
        if (!Objects.equals(match.hostPlayerId, requesterPlayerId)) return;
        if (match.gameStarted) return;
        if (getParticipantCount(match) >= match.requiredPlayers) return;

        Integer freeSlot = findNextFreeSlot(match);
        if (freeSlot == null) return;

        Player bot = createFreshPlayer(freeSlot);
        bot.bot = true;
        bot.ready = true;
        bot.displayName = "BOT_" + freeSlot;
        bot.characterName = "BOT_" + freeSlot;
        bot.gender = "male";
        bot.avatarCode = "bot";
        match.players.put(freeSlot, bot);

        // Quan trọng: tăng expectedBotCount để restart/sync không bị mất bot
        match.expectedBotCount = countCurrentBots(match);

        reevaluateWaitingState(match);
    }

    // =========================================================
    // Nếu chưa đủ số người thật thì chưa cho bot vào
    // Khi đủ rồi, tự sinh đúng số bot theo config room lobby
    // =========================================================
    private boolean maybeSyncConfiguredBots(MatchInstance match) {
        if (!match.customRoom) {
            return false;
        }

        boolean changed = false;

        // Chưa đủ người thật -> gỡ bot đi để chờ người thật vào
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

    private Integer findNextFreeSlot(MatchInstance match) {
        for (int id = 1; id <= match.requiredPlayers; id++) {
            if (!isOccupiedSlot(match, id)) {
                return id;
            }
        }
        return null;
    }

    private int countCurrentBots(MatchInstance match) {
        int count = 0;
        for (Player p : match.players.values()) {
            if (p != null && p.bot) {
                count++;
            }
        }
        return count;
    }

    private Integer findNextHumanHost(MatchInstance match) {
        return match.sessions.keySet().stream().sorted().findFirst().orElse(null);
    }

    private boolean isOccupiedSlot(MatchInstance match, int playerId) {
        return match.sessions.containsKey(playerId) || isBotSlot(match, playerId);
    }

    private boolean isBotSlot(MatchInstance match, int playerId) {
        Player player = match.players.get(playerId);
        return player != null && player.bot;
    }

    // Người thật hoặc bot đều được điều khiển
    private boolean canControlPlayer(MatchInstance match, int playerId) {
        return match.sessions.containsKey(playerId) || isBotSlot(match, playerId);
    }

    private boolean isActiveParticipant(MatchInstance match, Player player) {
        return player != null && (match.sessions.containsKey(player.id) || player.bot);
    }

    private int getParticipantCount(MatchInstance match) {
        int count = 0;
        for (int id = 1; id <= match.requiredPlayers; id++) {
            if (isOccupiedSlot(match, id)) {
                count++;
            }
        }
        return count;
    }

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
    // Tạo player mới với stat mặc định
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

        player.bot = false;
        player.ready = false;
        player.botNextThinkAt = 0L;
        player.botBombCooldownUntil = 0L;

        player.inventory = new ArrayList<>();
        return player;
    }

    // =========================================================
    // Gắn profile từ JWT vào player thật
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

    private String extractTokenFromSession(WebSocketSession session) {
        return extractQueryParam(session, "token");
    }

    private String extractRoomCodeFromSession(WebSocketSession session) {
        String value = extractQueryParam(session, "roomCode");
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

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
    // Tính lại trạng thái waiting / countdown
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

    private void startMatchNow(MatchInstance match) {
        match.gameStarted = true;
        match.waitingForPlayers = false;
        match.countdownSeconds = 0;
        match.countdownStartedAt = null;
        match.resultMessage = "Trận đấu bắt đầu";
        match.matchStartedAt = Instant.now();
        match.historySaved = false;
    }

    private long getMoveCooldown(Player player) {
        return gameConfig.getMoveCooldownForSpeedLevel(player.speedLevel);
    }

    private void updateOvr(Player player) {
        int score = player.kills * 100
                + player.lives * 50
                + player.bombsPlaced * 10
                - player.deaths * 20;
        player.ovr = Math.max(0, score);
    }

    private void updateAllOvr(MatchInstance match) {
        for (Player p : match.players.values()) {
            if (p != null) {
                updateOvr(p);
            }
        }
    }

    private boolean isPlayerFrozen(Player player, long now) {
        return player.frozenUntil > 0 && now < player.frozenUntil;
    }

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
    // Skill tăng số bom
    // - Người thật dùng được
    // - Bot cũng dùng được
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
    // Skill tăng tốc
    // - Người thật dùng được
    // - Bot cũng dùng được
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
    // Dùng item
    // Bot cũng được dùng item nên dùng canControlPlayer(...)
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
            case BOMB_UP -> {
                player.maxBombs = Math.min(
                        player.maxBombs + 1,
                        gameConfig.getPlayer().getMaxBombs()
                );
            }

            case FLAME_UP -> {
                player.bombRange = Math.min(
                        player.bombRange + 1,
                        gameConfig.getPlayer().getMaxBombRange()
                );
            }

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

            case SHIELD -> {
                player.invulnerableUntil = Math.max(
                        player.invulnerableUntil,
                        now + gameConfig.getPlayer().getShieldDurationMs()
                );
            }

            case HEART -> {
                player.lives = Math.min(
                        player.lives + 1,
                        gameConfig.getPlayer().getMaxLives()
                );
            }

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
    // Tick game chính
    // =========================================================
    @Scheduled(fixedRateString = "${game.match.tick-rate-ms:100}")
    public synchronized void tick() {
        List<String> matchKeys = new ArrayList<>(matches.keySet());

        for (String matchKey : matchKeys) {
            MatchInstance match = matches.get(matchKey);
            if (match == null) continue;

            boolean changed = false;
            long now = System.currentTimeMillis();

            // Countdown bắt đầu trận
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
    // Update bot:
    // 1) skill bomb
    // 2) skill speed
    // 3) item
    // 4) đặt bom
    // 5) di chuyển
    // =========================================================
    private boolean updateBots(MatchInstance match, long now) {
        if (!match.customRoom) return false;
        if (!match.gameStarted || match.gameOver) return false;

        boolean changed = false;
        List<Player> activePlayers = getActivePlayers(match);

        for (Player bot : activePlayers) {
            if (bot == null || !bot.bot || bot.lives <= 0) continue;

            BotAiService.BotDecision decision = botAiService.decide(
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

                    // Vừa đặt bom xong thì cho bot suy nghĩ lại ngay
                    // để nó chuyển sang mode chạy thoát
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
                for (FlameCell cell : explosion.cells) {
                    if (cell.row == victim.row && cell.col == victim.col) {
                        if (explosion.freezeEffect) {
                            hitFreezeBomb = true;
                        } else {
                            hitNormalBomb = true;
                            killerId = explosion.ownerId;
                            break outer;
                        }
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

    private void checkWinner(MatchInstance match) {
        if (match.gameOver) return;
        if (!match.gameStarted) return;

        List<Player> alivePlayers = match.players.values().stream()
                .filter(p -> isActiveParticipant(match, p))
                .filter(p -> p.lives > 0)
                .toList();

        if (alivePlayers.size() == 1) {
            Player winner = alivePlayers.get(0);
            match.gameOver = true;
            match.winnerId = winner.id;
            match.resultMessage = winner.characterName + " thắng trận";
            updateAllOvr(match);
            saveMatchHistoryIfNeeded(match);
        } else if (alivePlayers.isEmpty()) {
            match.gameOver = true;
            match.winnerId = null;
            match.resultMessage = "Hòa - không còn người sống";
            updateAllOvr(match);
            saveMatchHistoryIfNeeded(match);
        }
    }

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
            if (winner != null) {
                history.setWinnerUserId(winner.userId);
                history.setWinnerCharacterName(winner.characterName);
            }

            for (Player player : match.players.values()) {
                if (player == null) continue;
                if (player.bot) continue;
                if (player.userId == null || player.userId.isBlank()) continue;

                if (!participantUserIds.contains(player.userId)) {
                    participantUserIds.add(player.userId);
                }

                MatchHistoryDocument.PlayerMatchResult result = new MatchHistoryDocument.PlayerMatchResult();
                result.setUserId(player.userId);
                result.setCharacterName(player.characterName);
                result.setBombsPlaced(player.bombsPlaced);
                result.setKills(player.kills);
                result.setDeaths(player.deaths);
                result.setLivesLeft(Math.max(player.lives, 0));
                result.setOvr(player.ovr);
                result.setWinner(Objects.equals(player.id, match.winnerId));

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

    private List<FlameCell> buildExplosionCells(MatchInstance match, Bomb bomb) {
        if (bomb.randomPattern) {
            return buildRandomExplosionCells(match, bomb.row, bomb.col, bomb.range);
        }
        return buildNormalExplosionCells(match, bomb.row, bomb.col, bomb.range);
    }

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

    private boolean inBounds(int row, int col) {
        return row >= 0 && row < gameConfig.getBoard().getRows()
                && col >= 0 && col < gameConfig.getBoard().getCols();
    }

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
    // Reset players về trạng thái ban đầu
    // Lưu ý: chỉ reset đúng số slot của trận hiện tại
    // =========================================================
    private void resetPlayersOnly(MatchInstance match) {
        match.players.clear();

        for (int id = 1; id <= match.requiredPlayers; id++) {
            match.players.put(id, createFreshPlayer(id));
        }

        for (Integer playerId : match.sessions.keySet()) {
            WebSocketSession session = match.sessions.get(playerId);
            if (session != null) {
                attachProfileToPlayer(match, session, playerId);
            }
        }

        updateAllOvr(match);
    }

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
                match.countdownSeconds
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

    private void send(WebSocketSession session, ServerMessage message) {
        try {
            if (session != null && session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            }
        } catch (IOException ignored) {
        }
    }

    private int[][] copyBoard(MatchInstance match) {
        int rows = gameConfig.getBoard().getRows();
        int cols = gameConfig.getBoard().getCols();
        int[][] copied = new int[rows][cols];

        for (int i = 0; i < rows; i++) {
            System.arraycopy(match.board[i], 0, copied[i], 0, cols);
        }

        return copied;
    }

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

    private List<Item> copyItems(MatchInstance match) {
        List<Item> list = new ArrayList<>();

        for (Item i : match.items) {
            list.add(new Item(i.id, i.row, i.col, i.type));
        }

        return list;
    }

    // =========================================================
    // Trạng thái nội bộ của 1 trận
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
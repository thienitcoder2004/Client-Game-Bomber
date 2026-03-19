package com.bomberserver.backend.service;

import com.example.bomberserver.config.GameConfigProperties;
import com.example.bomberserver.document.CharacterProfileDocument;
import com.example.bomberserver.document.MatchHistoryDocument;
import com.example.bomberserver.dto.ClientMessage;
import com.example.bomberserver.dto.ServerMessage;
import com.example.bomberserver.model.*;
import com.example.bomberserver.repository.CharacterProfileRepository;
import com.example.bomberserver.security.JwtService;
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

    private static final String QUICK_PLAY_KEY = "QUICK-PLAY";

    private final ObjectMapper objectMapper;
    private final GameConfigProperties gameConfig;
    private final Random random = new Random();
    private final JwtService jwtService;
    private final CharacterProfileRepository characterProfileRepository;
    private final HistoryService historyService;

    private final Map<String, MatchInstance> matches = new ConcurrentHashMap<>();

    public GameRoomService(
            ObjectMapper objectMapper,
            GameConfigProperties gameConfig,
            JwtService jwtService,
            CharacterProfileRepository characterProfileRepository,
            HistoryService historyService
    ) {
        this.objectMapper = objectMapper;
        this.gameConfig = gameConfig;
        this.jwtService = jwtService;
        this.characterProfileRepository = characterProfileRepository;
        this.historyService = historyService;
    }

    public synchronized Integer join(WebSocketSession session) {
        String matchKey = extractRoomCodeFromSession(session);
        int requiredPlayers = extractRequiredPlayersFromSession(session);

        if (matchKey == null || matchKey.isBlank()) {
            matchKey = QUICK_PLAY_KEY;
        }

        if (QUICK_PLAY_KEY.equals(matchKey)) {
            requiredPlayers = gameConfig.getMatch().getDefaultRequiredPlayers();
        }

        if (requiredPlayers < gameConfig.getMatch().getMinRequiredPlayers()
                || requiredPlayers > gameConfig.getMatch().getMaxRequiredPlayers()) {
            requiredPlayers = gameConfig.getMatch().getDefaultRequiredPlayers();
        }

        final String finalMatchKey = matchKey;
        final int finalRequiredPlayers = requiredPlayers;

        MatchInstance match = matches.computeIfAbsent(
                finalMatchKey,
                key -> createMatch(key, finalRequiredPlayers)
        );

        for (int id = 1; id <= match.requiredPlayers; id++) {
            if (!match.sessions.containsKey(id)) {
                match.sessions.put(id, session);
                session.getAttributes().put("playerId", id);
                session.getAttributes().put("matchKey", finalMatchKey);

                attachProfileToPlayer(match, session, id);
                reevaluateWaitingState(match);
                return id;
            }
        }

        return null;
    }

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

    public synchronized void handleClientMessage(String matchKey, int playerId, ClientMessage message) {
        MatchInstance match = matches.get(matchKey);
        if (match == null || message == null || message.type == null) return;

        switch (message.type) {
            case "move" -> {
                if (!match.gameOver && match.gameStarted) handleMove(match, playerId, message.direction);
            }
            case "bomb" -> {
                if (!match.gameOver && match.gameStarted) handlePlaceBomb(match, playerId);
            }
            case "use_item" -> {
                if (!match.gameOver && match.gameStarted) handleUseItem(match, playerId, message.slotIndex);
            }
            case "restart" -> handleRestart(match, playerId);
            default -> {
            }
        }

        broadcastState(matchKey);
    }

    private MatchInstance createMatch(String matchKey, int requiredPlayers) {
        MatchInstance match = new MatchInstance();
        match.matchKey = matchKey;
        match.requiredPlayers = requiredPlayers;
        resetMatch(match);
        return match;
    }

    private void handleRestart(MatchInstance match, int playerId) {
        if (!match.sessions.containsKey(playerId)) return;
        resetMatch(match);
        reevaluateWaitingState(match);
    }

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
        if (value == null || value.isBlank()) return gameConfig.getMatch().getDefaultRequiredPlayers();

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return gameConfig.getMatch().getDefaultRequiredPlayers();
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

    private void reevaluateWaitingState(MatchInstance match) {
        int connected = match.sessions.size();

        if (match.gameStarted) {
            return;
        }

        if (connected >= match.requiredPlayers) {
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
            updateOvr(p);
        }
    }

    private void handleMove(MatchInstance match, int playerId, String directionRaw) {
        Player player = match.players.get(playerId);
        if (player == null || directionRaw == null) return;
        if (!match.sessions.containsKey(playerId)) return;
        if (player.lives <= 0) return;

        Direction direction;
        try {
            direction = Direction.valueOf(directionRaw);
        } catch (IllegalArgumentException ex) {
            return;
        }

        long now = System.currentTimeMillis();
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
        if (!match.sessions.containsKey(playerId)) return;
        if (player.lives <= 0) return;

        long activeBombs = match.bombs.stream()
                .filter(b -> b.ownerId == playerId)
                .count();

        if (activeBombs >= player.maxBombs) return;

        boolean exists = match.bombs.stream()
                .anyMatch(b -> b.row == player.row && b.col == player.col);

        if (exists) return;

        match.bombs.add(new Bomb(
                UUID.randomUUID().toString(),
                playerId,
                player.row,
                player.col,
                System.currentTimeMillis(),
                player.bombRange
        ));

        player.bombsPlaced += 1;
        updateOvr(player);
    }

    private void handleUseItem(MatchInstance match, int playerId, Integer slotIndex) {
        Player player = match.players.get(playerId);
        if (player == null || slotIndex == null) return;
        if (!match.sessions.containsKey(playerId)) return;
        if (player.lives <= 0) return;
        if (slotIndex < 0 || slotIndex >= player.inventory.size()) return;

        ItemType item = player.inventory.remove((int) slotIndex);
        long now = System.currentTimeMillis();

        switch (item) {
            case BOMB_UP -> player.maxBombs = Math.min(player.maxBombs + 1, gameConfig.getPlayer().getMaxBombs());
            case FLAME_UP -> player.bombRange = Math.min(player.bombRange + 1, gameConfig.getPlayer().getMaxBombRange());
            case SPEED_UP -> player.speedLevel = Math.min(player.speedLevel + 1, gameConfig.getPlayer().getMaxSpeedLevel());
            case SHIELD -> player.invulnerableUntil = Math.max(player.invulnerableUntil, now + gameConfig.getPlayer().getShieldDurationMs());
            case HEART -> player.lives = Math.min(player.lives + 1, gameConfig.getPlayer().getMaxLives());
        }

        updateOvr(player);
    }

    @Scheduled(fixedRateString = "${game.match.tick-rate-ms:100}")
    public synchronized void tick() {
        List<String> matchKeys = new ArrayList<>(matches.keySet());

        for (String matchKey : matchKeys) {
            MatchInstance match = matches.get(matchKey);
            if (match == null) continue;

            boolean changed = false;
            long now = System.currentTimeMillis();

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

            List<Bomb> expired = match.bombs.stream()
                    .filter(b -> now - b.placedAt >= gameConfig.getTiming().getBombFuseMs())
                    .toList();

            if (!expired.isEmpty()) {
                detonateBombs(match, expired, now);
                gameChanged = true;
            }

            int beforeExplosions = match.explosions.size();
            match.explosions.removeIf(e -> now - e.startedAt >= e.duration);
            if (beforeExplosions != match.explosions.size()) {
                gameChanged = true;
            }

            if (applyDamage(match, now)) {
                gameChanged = true;
            }

            if (changed || gameChanged) {
                broadcastState(matchKey);
            }
        }
    }

    private void detonateBombs(MatchInstance match, List<Bomb> expired, long now) {
        Queue<Bomb> queue = new ArrayDeque<>(expired);
        Set<String> detonatedIds = new HashSet<>();

        while (!queue.isEmpty()) {
            Bomb bomb = queue.poll();
            if (!detonatedIds.add(bomb.id)) continue;

            List<FlameCell> cells = buildExplosionCells(match, bomb.row, bomb.col, bomb.range);

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
                    cells
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

    private boolean applyDamage(MatchInstance match, long now) {
        boolean changed = false;

        for (Player victim : match.players.values()) {
            if (!match.sessions.containsKey(victim.id)) continue;
            if (victim.lives <= 0) continue;
            if (now < victim.invulnerableUntil) continue;

            Integer killerId = null;
            boolean hit = false;

            for (Explosion explosion : match.explosions) {
                for (FlameCell cell : explosion.cells) {
                    if (cell.row == victim.row && cell.col == victim.col) {
                        hit = true;
                        killerId = explosion.ownerId;
                        break;
                    }
                }
                if (hit) break;
            }

            if (!hit) continue;

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
                .filter(p -> match.sessions.containsKey(p.id))
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
                if (!match.sessions.containsKey(player.id)) continue;
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
                ItemType.HEART
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
            if (!match.sessions.containsKey(player.id)) continue;
            if (player.lives <= 0) continue;
            if (player.id != movingPlayerId && player.row == row && player.col == col) {
                return false;
            }
        }

        return true;
    }

    private List<FlameCell> buildExplosionCells(MatchInstance match, int row, int col, int range) {
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

    private boolean inBounds(int row, int col) {
        return row >= 0 && row < gameConfig.getBoard().getRows() && col >= 0 && col < gameConfig.getBoard().getCols();
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

    private void resetPlayersOnly(MatchInstance match) {
        match.players.clear();
        match.players.put(1, new Player(1, gameConfig.getSpawn().getP1Row(), gameConfig.getSpawn().getP1Col(), Direction.down, gameConfig.getPlayer().getStartLives()));
        match.players.put(2, new Player(2, gameConfig.getSpawn().getP2Row(), gameConfig.getSpawn().getP2Col(), Direction.down, gameConfig.getPlayer().getStartLives()));
        match.players.put(3, new Player(3, gameConfig.getSpawn().getP3Row(), gameConfig.getSpawn().getP3Col(), Direction.down, gameConfig.getPlayer().getStartLives()));
        match.players.put(4, new Player(4, gameConfig.getSpawn().getP4Row(), gameConfig.getSpawn().getP4Col(), Direction.down, gameConfig.getPlayer().getStartLives()));

        for (Player player : match.players.values()) {
            player.maxBombs = gameConfig.getPlayer().getStartMaxBombs();
            player.bombRange = gameConfig.getPlayer().getStartBombRange();
            player.speedLevel = gameConfig.getPlayer().getStartSpeedLevel();
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
                match.sessions.size(),
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
            if (!match.sessions.containsKey(p.id)) continue;

            Player copy = new Player(p.id, p.row, p.col, p.direction, p.lives);
            copy.invulnerableUntil = p.invulnerableUntil;
            copy.maxBombs = p.maxBombs;
            copy.bombRange = p.bombRange;
            copy.speedLevel = p.speedLevel;
            copy.bombsPlaced = p.bombsPlaced;
            copy.kills = p.kills;
            copy.deaths = p.deaths;
            copy.ovr = p.ovr;
            copy.userId = p.userId;
            copy.characterName = p.characterName;
            copy.gender = p.gender;
            copy.avatarCode = p.avatarCode;
            copy.inventory = new ArrayList<>(p.inventory);
            list.add(copy);
        }
        list.sort(Comparator.comparingInt(p -> p.id));
        return list;
    }

    private List<Bomb> copyBombs(MatchInstance match) {
        List<Bomb> list = new ArrayList<>();
        for (Bomb b : match.bombs) {
            list.add(new Bomb(b.id, b.ownerId, b.row, b.col, b.placedAt, b.range));
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
            list.add(new Explosion(e.id, e.ownerId, e.row, e.col, e.startedAt, e.duration, copiedCells));
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

    private static class MatchInstance {
        String matchKey;
        int requiredPlayers;

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
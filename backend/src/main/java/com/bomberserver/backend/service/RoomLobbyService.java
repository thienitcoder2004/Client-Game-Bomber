package com.bomberserver.backend.service;

import com.bomberserver.backend.config.GameConfigProperties;
import com.bomberserver.backend.document.CharacterProfileDocument;
import com.bomberserver.backend.dto.ClientMessage;
import com.bomberserver.backend.dto.ServerMessage;
import com.bomberserver.backend.repository.CharacterProfileRepository;
import com.bomberserver.backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomLobbyService {

    private final ObjectMapper objectMapper;
    private final GameConfigProperties gameConfig;
    private final JwtService jwtService;
    private final CharacterProfileRepository characterProfileRepository;

    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    private final Map<String, RoomInfo> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToRoom = new ConcurrentHashMap<>();

    public RoomLobbyService(
            ObjectMapper objectMapper,
            GameConfigProperties gameConfig,
            JwtService jwtService,
            CharacterProfileRepository characterProfileRepository
    ) {
        this.objectMapper = objectMapper;
        this.gameConfig = gameConfig;
        this.jwtService = jwtService;
        this.characterProfileRepository = characterProfileRepository;
    }

    // =========================================================
    // Kết nối room lobby
    // =========================================================
    public synchronized void register(WebSocketSession session) {
        SessionInfo info = buildSessionInfo(session);
        sessions.put(session.getId(), info);

        Map<String, Object> init = new HashMap<>();
        init.put("clientId", session.getId());
        send(session, new ServerMessage("init", init));

        sendRooms(session);
        sendCurrentRoomState(session);
    }

    public synchronized void unregister(WebSocketSession session) {
        leaveCurrentRoom(session.getId(), false);
        sessions.remove(session.getId());
        sessionToRoom.remove(session.getId());
        broadcastRooms();
    }

    // =========================================================
    // Nhận message từ frontend room lobby
    // =========================================================
    public synchronized void handleClientMessage(WebSocketSession session, ClientMessage message) {
        if (message == null || message.type == null) return;

        switch (message.type) {
            case "list_rooms" -> sendRooms(session);
            case "create_room" -> handleCreateRoom(session, message);
            case "join_room" -> handleJoinRoom(session, message.roomCode);

            case "leave_room" -> {
                leaveCurrentRoom(session.getId(), true);
                sendCurrentRoomState(session);
                broadcastRooms();
            }

            case "start_room" -> handleStartRoom(session);
            case "add_bot" -> handleAddBot(session);

            // ===== thêm mới =====
            case "remove_bot" -> handleRemoveBot(session, message.targetClientId);
            case "kick_member" -> handleKickMember(session, message.targetClientId);

            default -> {
            }
        }
    }

    // =========================================================
    // Tạo session info từ JWT/profile
    // =========================================================
    private SessionInfo buildSessionInfo(WebSocketSession session) {
        String userId = "";
        String characterName = "Người chơi";

        try {
            String token = extractTokenFromSession(session);
            if (token != null && !token.isBlank() && jwtService.isTokenValid(token)) {
                userId = jwtService.extractUserId(token);
                if (userId != null && !userId.isBlank()) {
                    CharacterProfileDocument profile = characterProfileRepository.findByUserId(userId).orElse(null);
                    if (profile != null && profile.getCharacterName() != null && !profile.getCharacterName().isBlank()) {
                        characterName = profile.getCharacterName();
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return new SessionInfo(session, userId == null ? "" : userId, characterName);
    }

    // =========================================================
    // Tạo phòng
    // =========================================================
    private void handleCreateRoom(WebSocketSession session, ClientMessage message) {
        int maxPlayers = message.maxPlayers == null
                ? gameConfig.getMatch().getDefaultRequiredPlayers()
                : message.maxPlayers;

        if (maxPlayers < gameConfig.getMatch().getMinRequiredPlayers()
                || maxPlayers > gameConfig.getMatch().getMaxRequiredPlayers()) {
            sendError(session, "Số người tối đa phải nằm trong khoảng "
                    + gameConfig.getMatch().getMinRequiredPlayers()
                    + " - "
                    + gameConfig.getMatch().getMaxRequiredPlayers());
            return;
        }

        leaveCurrentRoom(session.getId(), false);

        SessionInfo owner = sessions.get(session.getId());
        if (owner == null) {
            sendError(session, "Không tìm thấy phiên người chơi");
            return;
        }

        String roomCode = generateRoomCode();
        String roomName = message.roomName == null || message.roomName.isBlank()
                ? "Phòng của " + owner.characterName
                : message.roomName.trim();

        RoomInfo room = new RoomInfo();
        room.roomCode = roomCode;
        room.roomName = roomName;
        room.hostSessionId = session.getId();
        room.maxPlayers = maxPlayers;
        room.isPrivate = Boolean.TRUE.equals(message.isPrivate);
        room.status = "WAITING";
        room.memberSessionIds.add(session.getId());

        rooms.put(roomCode, room);
        sessionToRoom.put(session.getId(), roomCode);

        Map<String, Object> created = new HashMap<>();
        created.put("roomCode", roomCode);
        send(session, new ServerMessage("room_created", created));

        sendRoomStateToMembers(room);
        broadcastRooms();
    }

    // =========================================================
    // Vào phòng
    // =========================================================
    private void handleJoinRoom(WebSocketSession session, String roomCodeRaw) {
        if (roomCodeRaw == null || roomCodeRaw.isBlank()) {
            sendError(session, "Bạn chưa nhập mã phòng");
            return;
        }

        String roomCode = roomCodeRaw.trim().toUpperCase();
        RoomInfo room = rooms.get(roomCode);

        if (room == null) {
            sendError(session, "Không tìm thấy phòng");
            return;
        }

        if (!"WAITING".equals(room.status)) {
            sendError(session, "Phòng này đang chơi");
            return;
        }

        if (getTotalMemberCount(room) >= room.maxPlayers) {
            sendError(session, "Phòng đã đầy");
            return;
        }

        leaveCurrentRoom(session.getId(), false);

        room.memberSessionIds.add(session.getId());
        sessionToRoom.put(session.getId(), room.roomCode);

        sendRoomStateToMembers(room);
        broadcastRooms();
    }

    // =========================================================
    // Thêm bot
    // =========================================================
    private void handleAddBot(WebSocketSession session) {
        String roomCode = sessionToRoom.get(session.getId());
        if (roomCode == null) {
            sendError(session, "Bạn chưa ở trong phòng nào");
            return;
        }

        RoomInfo room = rooms.get(roomCode);
        if (room == null) {
            sendError(session, "Không tìm thấy phòng");
            return;
        }

        if (!Objects.equals(room.hostSessionId, session.getId())) {
            sendError(session, "Chỉ chủ phòng mới được thêm bot");
            return;
        }

        if (!"WAITING".equals(room.status)) {
            sendError(session, "Chỉ được thêm bot khi phòng đang chờ");
            return;
        }

        if (getTotalMemberCount(room) >= room.maxPlayers) {
            sendError(session, "Phòng đã đầy");
            return;
        }

        String botId = "BOT_" + (room.botMembers.size() + 1);
        BotMember bot = new BotMember(botId, botId);
        room.botMembers.put(botId, bot);

        sendRoomStateToMembers(room);
        broadcastRooms();
    }

    // =========================================================
    // Xóa bot - chỉ host được làm
    // =========================================================
    private void handleRemoveBot(WebSocketSession session, String targetClientId) {
        if (targetClientId == null || targetClientId.isBlank()) {
            sendError(session, "Thiếu bot cần xóa");
            return;
        }

        String roomCode = sessionToRoom.get(session.getId());
        if (roomCode == null) {
            sendError(session, "Bạn chưa ở trong phòng nào");
            return;
        }

        RoomInfo room = rooms.get(roomCode);
        if (room == null) {
            sendError(session, "Không tìm thấy phòng");
            return;
        }

        if (!Objects.equals(room.hostSessionId, session.getId())) {
            sendError(session, "Chỉ chủ phòng mới được xóa bot");
            return;
        }

        if (!"WAITING".equals(room.status)) {
            sendError(session, "Chỉ được xóa bot khi phòng đang chờ");
            return;
        }

        BotMember removed = room.botMembers.remove(targetClientId);
        if (removed == null) {
            sendError(session, "Không tìm thấy bot trong phòng");
            return;
        }

        sendRoomStateToMembers(room);
        broadcastRooms();
    }

    // =========================================================
    // Kick người chơi khác - chỉ host được làm
    // =========================================================
    private void handleKickMember(WebSocketSession session, String targetClientId) {
        if (targetClientId == null || targetClientId.isBlank()) {
            sendError(session, "Thiếu người chơi cần mời ra");
            return;
        }

        String roomCode = sessionToRoom.get(session.getId());
        if (roomCode == null) {
            sendError(session, "Bạn chưa ở trong phòng nào");
            return;
        }

        RoomInfo room = rooms.get(roomCode);
        if (room == null) {
            sendError(session, "Không tìm thấy phòng");
            return;
        }

        if (!Objects.equals(room.hostSessionId, session.getId())) {
            sendError(session, "Chỉ chủ phòng mới được mời người chơi ra");
            return;
        }

        if (!"WAITING".equals(room.status)) {
            sendError(session, "Chỉ được mời người chơi ra khi phòng đang chờ");
            return;
        }

        if (Objects.equals(targetClientId, room.hostSessionId)) {
            sendError(session, "Không thể mời chính chủ phòng ra");
            return;
        }

        if (!room.memberSessionIds.contains(targetClientId)) {
            sendError(session, "Không tìm thấy người chơi trong phòng");
            return;
        }

        room.memberSessionIds.remove(targetClientId);
        sessionToRoom.remove(targetClientId);

        SessionInfo kicked = sessions.get(targetClientId);
        if (kicked != null) {
            send(kicked.session, new ServerMessage("error", "Bạn đã bị chủ phòng mời ra khỏi phòng"));
            send(kicked.session, new ServerMessage("room_state", null));
            sendRooms(kicked.session);
        }

        sendRoomStateToMembers(room);
        broadcastRooms();
    }

    // =========================================================
    // Chủ phòng bấm Chơi
    // =========================================================
    private void handleStartRoom(WebSocketSession session) {
        String roomCode = sessionToRoom.get(session.getId());
        if (roomCode == null) {
            sendError(session, "Bạn chưa ở trong phòng nào");
            return;
        }

        RoomInfo room = rooms.get(roomCode);
        if (room == null) {
            sendError(session, "Không tìm thấy phòng");
            return;
        }

        if (!Objects.equals(room.hostSessionId, session.getId())) {
            sendError(session, "Chỉ chủ phòng mới được bấm Chơi");
            return;
        }

        if (getTotalMemberCount(room) < room.maxPlayers) {
            sendError(session, "Phòng chưa đủ người");
            return;
        }

        room.status = "PLAYING";
        sendRoomStateToMembers(room);
        broadcastRooms();

        Map<String, Object> started = new HashMap<>();
        started.put("roomCode", room.roomCode);
        started.put("roomName", room.roomName);
        started.put("maxPlayers", room.maxPlayers);

        // số người thật trong room
        started.put("humanCount", room.memberSessionIds.size());

        // số bot trong room
        started.put("botCount", room.botMembers.size());

        for (String memberSessionId : room.memberSessionIds) {
            SessionInfo member = sessions.get(memberSessionId);
            if (member != null) {
                send(member.session, new ServerMessage("room_started", started));
            }
        }
    }

    // =========================================================
    // Rời phòng
    // =========================================================
    private void leaveCurrentRoom(String sessionId, boolean notifyMembers) {
        String roomCode = sessionToRoom.remove(sessionId);
        if (roomCode == null) return;

        RoomInfo room = rooms.get(roomCode);
        if (room == null) return;

        room.memberSessionIds.remove(sessionId);

        if (room.memberSessionIds.isEmpty()) {
            rooms.remove(roomCode);
            return;
        }

        if (Objects.equals(room.hostSessionId, sessionId)) {
            room.hostSessionId = room.memberSessionIds.iterator().next();
        }

        if (notifyMembers) {
            sendRoomStateToMembers(room);
        }
    }

    // =========================================================
    // Gửi trạng thái phòng hiện tại
    // =========================================================
    private void sendCurrentRoomState(WebSocketSession session) {
        String roomCode = sessionToRoom.get(session.getId());
        if (roomCode == null) {
            send(session, new ServerMessage("room_state", null));
            return;
        }

        RoomInfo room = rooms.get(roomCode);
        if (room == null) {
            send(session, new ServerMessage("room_state", null));
            return;
        }

        send(session, new ServerMessage("room_state", buildRoomState(room, session.getId())));
    }

    private void sendRoomStateToMembers(RoomInfo room) {
        for (String sessionId : room.memberSessionIds) {
            SessionInfo info = sessions.get(sessionId);
            if (info != null) {
                send(info.session, new ServerMessage("room_state", buildRoomState(room, sessionId)));
            }
        }
    }

    // =========================================================
    // Build dữ liệu room_state
    // =========================================================
    private Map<String, Object> buildRoomState(RoomInfo room, String viewerSessionId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roomCode", room.roomCode);
        data.put("roomName", room.roomName);
        data.put("maxPlayers", room.maxPlayers);
        data.put("playerCount", getTotalMemberCount(room));
        data.put("status", room.status);
        data.put("isPrivate", room.isPrivate);
        data.put("isHost", Objects.equals(room.hostSessionId, viewerSessionId));
        data.put("canStart", Objects.equals(room.hostSessionId, viewerSessionId)
                && getTotalMemberCount(room) == room.maxPlayers);

        SessionInfo host = sessions.get(room.hostSessionId);
        data.put("hostName", host != null ? host.characterName : "Chủ phòng");

        List<Map<String, Object>> members = new ArrayList<>();

        for (String memberSessionId : room.memberSessionIds) {
            SessionInfo member = sessions.get(memberSessionId);
            if (member == null) continue;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clientId", memberSessionId);
            m.put("characterName", member.characterName);
            m.put("host", Objects.equals(memberSessionId, room.hostSessionId));
            m.put("bot", false);
            members.add(m);
        }

        for (BotMember bot : room.botMembers.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clientId", bot.botId);
            m.put("characterName", bot.characterName);
            m.put("host", false);
            m.put("bot", true);
            members.add(m);
        }

        data.put("members", members);
        return data;
    }

    // =========================================================
    // Gửi danh sách phòng
    // =========================================================
    private void sendRooms(WebSocketSession session) {
        List<Map<String, Object>> list = buildPublicRoomSummaries();
        send(session, new ServerMessage("rooms", list));
    }

    private void broadcastRooms() {
        List<Map<String, Object>> list = buildPublicRoomSummaries();
        String json;

        try {
            json = objectMapper.writeValueAsString(new ServerMessage("rooms", list));
        } catch (Exception e) {
            return;
        }

        for (SessionInfo info : sessions.values()) {
            try {
                if (info.session.isOpen()) {
                    info.session.sendMessage(new TextMessage(json));
                }
            } catch (IOException ignored) {
            }
        }
    }

    private List<Map<String, Object>> buildPublicRoomSummaries() {
        List<Map<String, Object>> list = new ArrayList<>();

        for (RoomInfo room : rooms.values()) {
            if (room.isPrivate) continue;

            SessionInfo host = sessions.get(room.hostSessionId);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("roomCode", room.roomCode);
            item.put("roomName", room.roomName);
            item.put("hostName", host != null ? host.characterName : "Chủ phòng");
            item.put("playerCount", getTotalMemberCount(room));
            item.put("maxPlayers", room.maxPlayers);
            item.put("status", room.status);
            item.put("isPrivate", room.isPrivate);

            list.add(item);
        }

        list.sort(Comparator.comparing(m -> String.valueOf(m.get("roomCode"))));
        return list;
    }

    private int getTotalMemberCount(RoomInfo room) {
        return room.memberSessionIds.size() + room.botMembers.size();
    }

    private void send(WebSocketSession session, ServerMessage message) {
        try {
            if (session != null && session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            }
        } catch (IOException ignored) {
        }
    }

    public void sendError(WebSocketSession session, String text) {
        send(session, new ServerMessage("error", text));
    }

    private String generateRoomCode() {
        String roomCode;
        do {
            roomCode = "#" + (1000 + new Random().nextInt(9000));
        } while (rooms.containsKey(roomCode));
        return roomCode;
    }

    private String extractTokenFromSession(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null || uri.getQuery() == null) return null;

            String[] parts = uri.getQuery().split("&");
            for (String part : parts) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2 && "token".equals(kv[0])) {
                    return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static class SessionInfo {
        WebSocketSession session;
        String userId;
        String characterName;

        SessionInfo(WebSocketSession session, String userId, String characterName) {
            this.session = session;
            this.userId = userId;
            this.characterName = characterName == null || characterName.isBlank()
                    ? "Người chơi"
                    : characterName;
        }
    }

    private static class BotMember {
        String botId;
        String characterName;

        BotMember(String botId, String characterName) {
            this.botId = botId;
            this.characterName = characterName;
        }
    }

    private static class RoomInfo {
        String roomCode;
        String roomName;
        String hostSessionId;
        int maxPlayers;
        boolean isPrivate;
        String status;
        LinkedHashSet<String> memberSessionIds = new LinkedHashSet<>();
        LinkedHashMap<String, BotMember> botMembers = new LinkedHashMap<>();
    }
}
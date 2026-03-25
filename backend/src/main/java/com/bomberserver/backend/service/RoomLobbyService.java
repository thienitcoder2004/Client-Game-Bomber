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

    // =========================================================
    // Dependency dùng để:
    // - parse / build JSON websocket
    // - đọc config game
    // - giải mã JWT
    // - lấy profile người chơi
    // =========================================================
    private final ObjectMapper objectMapper;
    private final GameConfigProperties gameConfig;
    private final JwtService jwtService;
    private final CharacterProfileRepository characterProfileRepository;
    // =========================================================
    // MATCH MODE:
    // - SOLO = chơi đơn
    // - DUO  = chơi đôi 2v2
    private static final String MATCH_MODE_SOLO = "SOLO";
    private static final String MATCH_MODE_DUO = "DUO";

    // =========================================================
    // sessions:
    // - Lưu session websocket của lobby room
    // - key   = sessionId
    // - value = thông tin người chơi của session đó
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    // =========================================================
    // rooms:
    // - Lưu toàn bộ phòng đang tồn tại
    // - key   = roomCode
    // - value = thông tin phòng
    private final Map<String, RoomInfo> rooms = new ConcurrentHashMap<>();

    // =========================================================
    // sessionToRoom:
    // - Map sessionId -> roomCode
    // - Dùng để biết 1 session hiện đang ở phòng nào
    // =========================================================
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
    // HÀM: register
    // Mục đích:
    // - Khi 1 client mở websocket lobby
    // - Tạo SessionInfo cho client đó
    // - Gửi init, danh sách phòng, trạng thái phòng hiện tại
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

    // =========================================================
    // HÀM: sanitizeMatchMode
    // Mục đích:
    // - Chuẩn hóa kiểu trận
    // - Chỉ cho phép SOLO hoặc DUO
private String sanitizeMatchMode(String raw) {
    if (raw != null && MATCH_MODE_DUO.equalsIgnoreCase(raw.trim())) {
        return MATCH_MODE_DUO;
    }
    return MATCH_MODE_SOLO;
}

    // =========================================================
    // HÀM: unregister
    // Mục đích:
    // - Khi websocket lobby bị đóng
    //
    // Logic quan trọng:
    // 1) Nếu phòng còn đang WAITING:
    //    - coi như người chơi rời phòng thật
    //
    // 2) Nếu phòng đã PLAYING:
    //    - KHÔNG xóa room
    //    - KHÔNG làm mất trạng thái đang chơi
    //    - chỉ xóa session websocket lobby
    //
    // Nhờ vậy phòng vẫn hiện ngoài danh sách với trạng thái:
    // - Đang chơi
    // - Đủ người
    // - Thiếu người
    // =========================================================
    public synchronized void unregister(WebSocketSession session) {
        String sessionId = session.getId();
        String roomCode = sessionToRoom.get(sessionId);
        RoomInfo room = roomCode == null ? null : rooms.get(roomCode);

        // Nếu phòng chưa bắt đầu trận thì đóng socket được xem là rời phòng
        if (room != null && !"PLAYING".equals(room.status)) {
            leaveCurrentRoom(sessionId, false);
        } else {
            // Nếu đang PLAYING thì chỉ gỡ mapping session -> room,
            // không được xóa room
            sessionToRoom.remove(sessionId);
        }

        // Xóa session websocket khỏi danh sách session lobby
        sessions.remove(sessionId);

        // Broadcast lại danh sách phòng cho client lobby còn mở
        broadcastRooms();
    }

    // =========================================================
    // HÀM: handleClientMessage
    // Mục đích:
    // - Nhận action từ frontend lobby
    // - Điều hướng vào đúng hàm xử lý
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

            case "remove_bot" -> handleRemoveBot(session, message.targetClientId);

            case "kick_member" -> handleKickMember(session, message.targetClientId);

            default -> {
                // Không làm gì nếu type không hỗ trợ
            }
        }
    }

    // =========================================================
    // HÀM: buildSessionInfo
    // Mục đích:
    // - Lấy token từ websocket query param
    // - Giải mã token ra userId
    // - Từ userId lấy characterName từ profile
    // - Trả về SessionInfo
    // =========================================================
    private SessionInfo buildSessionInfo(WebSocketSession session) {
        String userId = "";
        String characterName = "Người chơi";

        try {
            String token = extractTokenFromSession(session);

            if (token != null && !token.isBlank() && jwtService.isTokenValid(token)) {
                userId = jwtService.extractUserId(token);

                if (userId != null && !userId.isBlank()) {
                    CharacterProfileDocument profile =
                            characterProfileRepository.findByUserId(userId).orElse(null);

                    if (profile != null
                            && profile.getCharacterName() != null
                            && !profile.getCharacterName().isBlank()) {
                        characterName = profile.getCharacterName();
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return new SessionInfo(
                session,
                userId == null ? "" : userId,
                characterName
        );
    }

    // =========================================================
// HÀM: handleCreateRoom
// Mục đích:
// - Tạo phòng mới
//
// Điểm sửa:
// - lưu thêm matchMode
// - nếu là DUO thì ép maxPlayers = 4 để đúng luật 2v2
// =========================================================
private void handleCreateRoom(WebSocketSession session, ClientMessage message) {
    String matchMode = sanitizeMatchMode(message.matchMode);

    int maxPlayers = message.maxPlayers == null
            ? gameConfig.getMatch().getDefaultRequiredPlayers()
            : message.maxPlayers;

    // DUO luôn chạy theo 2v2 => cố định 4 người
    if (MATCH_MODE_DUO.equals(matchMode)) {
        maxPlayers = 4;
    }

    if (maxPlayers < gameConfig.getMatch().getMinRequiredPlayers()
            || maxPlayers > gameConfig.getMatch().getMaxRequiredPlayers()) {
        sendError(
                session,
                "Số người tối đa phải nằm trong khoảng "
                        + gameConfig.getMatch().getMinRequiredPlayers()
                        + " - "
                        + gameConfig.getMatch().getMaxRequiredPlayers()
        );
        return;
    }

    // Nếu đang ở phòng cũ thì rời trước
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

    // Host hiện tại
    room.hostSessionId = session.getId();

    // Lưu cứng thông tin host để không phụ thuộc session đang mở
    room.hostUserId = owner.userId;
    room.hostCharacterName = owner.characterName;

    room.maxPlayers = maxPlayers;
    room.isPrivate = Boolean.TRUE.equals(message.isPrivate);
    room.status = "WAITING";

    // ===== lưu kiểu trận =====
    room.matchMode = matchMode;

    // Người tạo phòng tự động vào phòng
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
    // HÀM: handleJoinRoom
    // Mục đích:
    // - Cho người chơi vào phòng đang WAITING
    // - Không cho vào nếu:
    //   + không tìm thấy phòng
    //   + phòng đang PLAYING
    //   + phòng đã đầy
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

        // Rời phòng cũ trước khi vào phòng mới
        leaveCurrentRoom(session.getId(), false);

        room.memberSessionIds.add(session.getId());
        sessionToRoom.put(session.getId(), room.roomCode);

        sendRoomStateToMembers(room);
        broadcastRooms();
    }

    // =========================================================
    // HÀM: handleAddBot
    // Mục đích:
    // - Host thêm bot vào phòng
    // - Chỉ làm được khi phòng WAITING
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
    // HÀM: handleRemoveBot
    // Mục đích:
    // - Host xóa bot ra khỏi phòng
    // - Chỉ làm được khi phòng WAITING
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
    // HÀM: handleKickMember
    // Mục đích:
    // - Host mời người chơi khác ra khỏi phòng
    // - Không cho kick host
    // - Chỉ làm được khi phòng WAITING
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

        // Xóa target khỏi phòng
        room.memberSessionIds.remove(targetClientId);
        sessionToRoom.remove(targetClientId);

        // Báo cho người bị kick biết
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
// HÀM: handleStartRoom
// Mục đích:
// - Host bấm Chơi
// - Đổi status phòng sang PLAYING
// - Gửi room_started cho toàn bộ người thật trong phòng
//
// Điểm sửa:
// - gửi thêm matchMode để frontend chuyển tiếp sang /game
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

    // Cập nhật room_state cho người còn đang mở lobby
    sendRoomStateToMembers(room);

    // Broadcast cho toàn lobby để phòng hiện trạng thái "Đang chơi"
    broadcastRooms();

    Map<String, Object> started = new HashMap<>();
    started.put("roomCode", room.roomCode);
    started.put("roomName", room.roomName);
    started.put("maxPlayers", room.maxPlayers);
    started.put("humanCount", room.memberSessionIds.size());
    started.put("botCount", room.botMembers.size());

    // ===== gửi thêm kiểu trận =====
    started.put("matchMode", sanitizeMatchMode(room.matchMode));

    // Gửi room_started cho toàn bộ người thật trong phòng
    for (String memberSessionId : room.memberSessionIds) {
        SessionInfo member = sessions.get(memberSessionId);
        if (member != null) {
            send(member.session, new ServerMessage("room_started", started));
        }
    }
}

    // =========================================================
    // HÀM: leaveCurrentRoom
    // Mục đích:
    // - Cho 1 session rời khỏi phòng hiện tại
    //
    // Logic:
    // 1) Nếu room đang PLAYING:
    //    - không xóa room
    //    - vì room sẽ được xóa khi trận kết thúc thật sự
    //
    // 2) Nếu room đang WAITING:
    //    - nếu hết người thật -> xóa room
    //    - nếu host rời -> chuyển host cho người tiếp theo
    //      và cập nhật luôn hostCharacterName
    // =========================================================
    private void leaveCurrentRoom(String sessionId, boolean notifyMembers) {
        String roomCode = sessionToRoom.remove(sessionId);
        if (roomCode == null) return;

        RoomInfo room = rooms.get(roomCode);
        if (room == null) return;

        // Xóa người chơi khỏi danh sách member người thật
        room.memberSessionIds.remove(sessionId);

        // Nếu đang PLAYING thì không xóa room ở đây
        if ("PLAYING".equals(room.status)) {
            if (notifyMembers) {
                sendRoomStateToMembers(room);
            }
            return;
        }

        // Nếu không còn người thật nào thì xóa cả phòng
        if (room.memberSessionIds.isEmpty()) {
            rooms.remove(roomCode);
            return;
        }

        // Nếu host rời phòng khi còn WAITING
        // -> chuyển host cho người vào trước tiếp theo
        // -> cập nhật luôn tên host mới
        if (Objects.equals(room.hostSessionId, sessionId)) {
            String newHostSessionId = room.memberSessionIds.iterator().next();
            room.hostSessionId = newHostSessionId;

            SessionInfo newHost = sessions.get(newHostSessionId);
            if (newHost != null) {
                room.hostUserId = newHost.userId;
                room.hostCharacterName = newHost.characterName;
            } else {
                room.hostUserId = "";
                room.hostCharacterName = "Chủ phòng";
            }
        }

        if (notifyMembers) {
            sendRoomStateToMembers(room);
        }
    }

    // =========================================================
    // HÀM: finishRoom
    // Mục đích:
    // - Xóa room khỏi lobby khi trận đấu kết thúc thật sự
    // - Hàm này sẽ được GameRoomService gọi sang
    // =========================================================
    public synchronized void finishRoom(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) return;

        RoomInfo removed = rooms.remove(roomCode);
        if (removed == null) return;

        // Dọn mapping session -> room còn sót
        sessionToRoom.entrySet().removeIf(entry -> roomCode.equals(entry.getValue()));

        broadcastRooms();
    }

    // =========================================================
    // HÀM: sendCurrentRoomState
    // Mục đích:
    // - Gửi room_state hiện tại cho đúng client đó
    // - Nếu client không ở phòng nào -> room_state = null
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

    // =========================================================
    // HÀM: sendRoomStateToMembers
    // Mục đích:
    // - Gửi room_state cho tất cả member người thật trong phòng
    // =========================================================
    private void sendRoomStateToMembers(RoomInfo room) {
        for (String sessionId : room.memberSessionIds) {
            SessionInfo info = sessions.get(sessionId);
            if (info != null) {
                send(info.session, new ServerMessage("room_state", buildRoomState(room, sessionId)));
            }
        }
    }

    // =========================================================
// HÀM: buildRoomState
// Mục đích:
// - Tạo payload room_state cho frontend
//
// Điểm sửa:
// - gửi thêm matchMode
// =========================================================
private Map<String, Object> buildRoomState(RoomInfo room, String viewerSessionId) {
    Map<String, Object> data = new LinkedHashMap<>();

    data.put("roomCode", room.roomCode);
    data.put("roomName", room.roomName);
    data.put("maxPlayers", room.maxPlayers);
    data.put("playerCount", getTotalMemberCount(room));
    data.put("status", room.status);
    data.put("isPrivate", room.isPrivate);

    // ===== kiểu trận của phòng =====
    data.put("matchMode", sanitizeMatchMode(room.matchMode));

    // Viewer hiện tại có phải host không
    data.put("isHost", Objects.equals(room.hostSessionId, viewerSessionId));

    // Chỉ host mới start được và phải đủ người
    data.put(
            "canStart",
            Objects.equals(room.hostSessionId, viewerSessionId)
                    && getTotalMemberCount(room) == room.maxPlayers
    );

    // Không phụ thuộc session host đang còn mở hay không
    data.put(
            "hostName",
            room.hostCharacterName == null || room.hostCharacterName.isBlank()
                    ? "Chủ phòng"
                    : room.hostCharacterName
    );

    List<Map<String, Object>> members = new ArrayList<>();

    // Người thật
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

    // Bot
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
    // HÀM: sendRooms
    // Mục đích:
    // - Gửi danh sách public room cho 1 client
    // =========================================================
    private void sendRooms(WebSocketSession session) {
        List<Map<String, Object>> list = buildPublicRoomSummaries();
        send(session, new ServerMessage("rooms", list));
    }

    // =========================================================
    // HÀM: broadcastRooms
    // Mục đích:
    // - Broadcast danh sách public room cho toàn bộ client lobby
    // =========================================================
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

    // =========================================================
// HÀM: buildPublicRoomSummaries
// Mục đích:
// - Build dữ liệu danh sách phòng cho màn lobby
//
// Điểm sửa:
// - gửi thêm matchMode để danh sách phòng biết đây là SOLO hay DUO
// =========================================================
private List<Map<String, Object>> buildPublicRoomSummaries() {
    List<Map<String, Object>> list = new ArrayList<>();

    for (RoomInfo room : rooms.values()) {
        // Không hiện phòng private ở danh sách public
        if (room.isPrivate) continue;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("roomCode", room.roomCode);
        item.put("roomName", room.roomName);
        item.put(
                "hostName",
                room.hostCharacterName == null || room.hostCharacterName.isBlank()
                        ? "Chủ phòng"
                        : room.hostCharacterName
        );
        item.put("playerCount", getTotalMemberCount(room));
        item.put("maxPlayers", room.maxPlayers);
        item.put("status", room.status);
        item.put("isPrivate", room.isPrivate);

        // ===== kiểu trận =====
        item.put("matchMode", sanitizeMatchMode(room.matchMode));

        list.add(item);
    }

    list.sort(Comparator.comparing(m -> String.valueOf(m.get("roomCode"))));
    return list;
}

    // =========================================================
    // HÀM: getTotalMemberCount
    // Mục đích:
    // - Đếm tổng người trong phòng
    // - = người thật + bot
    // =========================================================
    private int getTotalMemberCount(RoomInfo room) {
        return room.memberSessionIds.size() + room.botMembers.size();
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
    // HÀM: sendError
    // Mục đích:
    // - Gửi message lỗi cho frontend
    // =========================================================
    public void sendError(WebSocketSession session, String text) {
        send(session, new ServerMessage("error", text));
    }

    // =========================================================
    // HÀM: generateRoomCode
    // Mục đích:
    // - Tạo mã phòng dạng #1234
    // - Đảm bảo không bị trùng
    // =========================================================
    private String generateRoomCode() {
        String roomCode;
        do {
            roomCode = "#" + (1000 + new Random().nextInt(9000));
        } while (rooms.containsKey(roomCode));
        return roomCode;
    }

    // =========================================================
    // HÀM: extractTokenFromSession
    // Mục đích:
    // - Lấy token từ query param của websocket
    // - Ví dụ:
    //   ws://localhost:8080/ws/rooms?token=abcxyz
    // =========================================================
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

    // =========================================================
    // CLASS PHỤ: SessionInfo
    // Mục đích:
    // - Lưu thông tin người chơi gắn với session lobby
    // =========================================================
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

    // =========================================================
    // CLASS PHỤ: BotMember
    // Mục đích:
    // - Lưu thông tin bot trong phòng
    // =========================================================
    private static class BotMember {
        String botId;
        String characterName;

        BotMember(String botId, String characterName) {
            this.botId = botId;
            this.characterName = characterName;
        }
    }

    // =========================================================
// CLASS PHỤ: RoomInfo
// Mục đích:
// - Lưu toàn bộ thông tin của 1 phòng
// =========================================================
private static class RoomInfo {
    String roomCode;
    String roomName;

    String hostSessionId;

    // Lưu cứng thông tin host
    String hostUserId;
    String hostCharacterName;

    int maxPlayers;
    boolean isPrivate;

    // WAITING hoặc PLAYING
    String status;

    // ===== SOLO hoặc DUO =====
    String matchMode = MATCH_MODE_SOLO;

    // Danh sách sessionId của người thật
    LinkedHashSet<String> memberSessionIds = new LinkedHashSet<>();

    // Danh sách bot
    LinkedHashMap<String, BotMember> botMembers = new LinkedHashMap<>();
}
}
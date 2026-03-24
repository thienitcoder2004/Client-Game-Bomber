package com.bomberserver.backend.service;

import com.bomberserver.backend.document.CharacterProfileDocument;
import com.bomberserver.backend.document.FriendChatMessageDocument;
import com.bomberserver.backend.document.FriendDocument;
import com.bomberserver.backend.document.UserAccountDocument;
import com.bomberserver.backend.dto.ServerMessage;
import com.bomberserver.backend.dto.friend.FriendChatClientMessage;
import com.bomberserver.backend.dto.friend.FriendChatMessageResponse;
import com.bomberserver.backend.repository.CharacterProfileRepository;
import com.bomberserver.backend.repository.FriendChatMessageRepository;
import com.bomberserver.backend.repository.FriendRepository;
import com.bomberserver.backend.repository.UserAccountRepository;
import com.bomberserver.backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class FriendChatSocketService {

    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final FriendRepository friendRepository;
    private final FriendChatMessageRepository friendChatMessageRepository;
    private final UserAccountRepository userAccountRepository;
    private final CharacterProfileRepository characterProfileRepository;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionUsers = new ConcurrentHashMap<>();

    public FriendChatSocketService(
            ObjectMapper objectMapper,
            JwtService jwtService,
            FriendRepository friendRepository,
            FriendChatMessageRepository friendChatMessageRepository,
            UserAccountRepository userAccountRepository,
            CharacterProfileRepository characterProfileRepository
    ) {
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.friendRepository = friendRepository;
        this.friendChatMessageRepository = friendChatMessageRepository;
        this.userAccountRepository = userAccountRepository;
        this.characterProfileRepository = characterProfileRepository;
    }

    public synchronized void register(WebSocketSession session) throws IOException {
        String token = extractTokenFromSession(session);
        if (token == null || token.isBlank() || !jwtService.isTokenValid(token)) {
            session.close();
            return;
        }

        String userId = jwtService.extractUserId(token);
        if (userId == null || userId.isBlank()) {
            session.close();
            return;
        }

        sessions.put(session.getId(), session);
        sessionUsers.put(session.getId(), userId);
        userSessions.computeIfAbsent(userId, key -> new HashSet<>()).add(session.getId());

        Map<String, Object> init = new HashMap<>();
        init.put("userId", userId);
        init.put("connected", true);
        send(session, new ServerMessage("init", init));
    }

    public synchronized void unregister(WebSocketSession session) {
        sessions.remove(session.getId());
        String userId = sessionUsers.remove(session.getId());
        if (userId == null) return;

        Set<String> ids = userSessions.get(userId);
        if (ids != null) {
            ids.remove(session.getId());
            if (ids.isEmpty()) {
                userSessions.remove(userId);
            }
        }
    }

    public synchronized void handleClientMessage(WebSocketSession session, FriendChatClientMessage message) {
        if (message == null || message.type == null) return;

        String senderId = sessionUsers.get(session.getId());
        if (senderId == null || senderId.isBlank()) {
            sendError(session, "Phiên đăng nhập không hợp lệ");
            return;
        }

        if ("send_message".equals(message.type)) {
            handleSendMessage(session, senderId, message.targetUserId, message.content);
        }
    }

    public boolean isUserOnline(String userId) {
        Set<String> ids = userSessions.get(userId);
        return ids != null && !ids.isEmpty();
    }

    private void handleSendMessage(WebSocketSession session, String senderId, String targetUserId, String contentRaw) {
        if (targetUserId == null || targetUserId.isBlank()) {
            sendError(session, "Thiếu người nhận");
            return;
        }

        String content = contentRaw == null ? "" : contentRaw.trim();
        if (content.isBlank()) {
            sendError(session, "Tin nhắn không được để trống");
            return;
        }

        if (content.length() > 500) {
            sendError(session, "Tin nhắn tối đa 500 ký tự");
            return;
        }

        UserAccountDocument receiver = userAccountRepository.findById(targetUserId).orElse(null);
        if (receiver == null) {
            sendError(session, "Không tìm thấy người nhận");
            return;
        }

        FriendDocument relation = findRelation(senderId, targetUserId).orElse(null);
        if (relation == null || !"ACCEPTED".equals(relation.getStatus())) {
            sendError(session, "Chỉ chat được với bạn bè đã chấp nhận");
            return;
        }

        FriendChatMessageDocument chat = new FriendChatMessageDocument();
        chat.setConversationKey(buildConversationKey(senderId, targetUserId));
        chat.setSenderId(senderId);
        chat.setReceiverId(targetUserId);
        chat.setContent(content);
        chat.setCreatedAt(Instant.now());
        chat = friendChatMessageRepository.save(chat);

        FriendChatMessageResponse response = new FriendChatMessageResponse(
                chat.getId(),
                chat.getSenderId(),
                chat.getReceiverId(),
                chat.getContent(),
                chat.getCreatedAt()
        );

        ServerMessage serverMessage = new ServerMessage("chat_message", response);
        sendToUser(senderId, serverMessage);
        sendToUser(targetUserId, serverMessage);
    }

    public FriendUserView getUserView(String userId) {
        UserAccountDocument user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

        CharacterProfileDocument profile = characterProfileRepository.findByUserId(userId)
                .orElseGet(() -> characterProfileRepository.save(new CharacterProfileDocument(userId)));

        return new FriendUserView(
                user.getId(),
                user.getUsername(),
                profile.getCharacterName(),
                profile.getAvatarCode(),
                isUserOnline(user.getId())
        );
    }

    private Optional<FriendDocument> findRelation(String userId1, String userId2) {
        String[] pair = sortPair(userId1, userId2);
        return friendRepository.findByUserAIdAndUserBId(pair[0], pair[1]);
    }

    public static String buildConversationKey(String userId1, String userId2) {
        String[] pair = sortPair(userId1, userId2);
        return pair[0] + "__" + pair[1];
    }

    private static String[] sortPair(String userId1, String userId2) {
        if (userId1.compareTo(userId2) <= 0) {
            return new String[]{userId1, userId2};
        }
        return new String[]{userId2, userId1};
    }

    private void sendToUser(String userId, ServerMessage payload) {
        Set<String> ids = userSessions.get(userId);
        if (ids == null) return;

        for (String sessionId : ids) {
            WebSocketSession target = sessions.get(sessionId);
            if (target != null) {
                send(target, payload);
            }
        }
    }

    private void sendError(WebSocketSession session, String message) {
        send(session, new ServerMessage("error", message));
    }

    private void send(WebSocketSession session, ServerMessage payload) {
        try {
            if (session != null && session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        } catch (Exception ignored) {
        }
    }

    private String extractTokenFromSession(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null || uri.getQuery() == null) return null;

            String[] parts = uri.getQuery().split("&");
            for (String part : parts) {
                int idx = part.indexOf('=');
                if (idx <= 0) continue;

                String key = URLDecoder.decode(part.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8);
                if ("token".equals(key)) {
                    return value;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public record FriendUserView(
            String userId,
            String username,
            String characterName,
            String avatarCode,
            boolean online
    ) {
    }
}
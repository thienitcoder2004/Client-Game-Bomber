package com.bomberserver.backend.ws;

import com.bomberserver.backend.dto.friend.FriendChatClientMessage;
import com.bomberserver.backend.service.FriendChatSocketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class FriendChatWebSocketHandler extends TextWebSocketHandler {

    private final FriendChatSocketService friendChatSocketService;
    private final ObjectMapper objectMapper;

    public FriendChatWebSocketHandler(FriendChatSocketService friendChatSocketService, ObjectMapper objectMapper) {
        this.friendChatSocketService = friendChatSocketService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        friendChatSocketService.register(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        FriendChatClientMessage clientMessage = objectMapper.readValue(message.getPayload(), FriendChatClientMessage.class);
        friendChatSocketService.handleClientMessage(session, clientMessage);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        friendChatSocketService.unregister(session);
    }
}
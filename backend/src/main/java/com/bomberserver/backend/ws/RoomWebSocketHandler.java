package com.bomberserver.backend.ws;

import com.example.bomberserver.dto.ClientMessage;
import com.example.bomberserver.service.RoomLobbyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private final RoomLobbyService roomLobbyService;
    private final ObjectMapper objectMapper;

    public RoomWebSocketHandler(RoomLobbyService roomLobbyService, ObjectMapper objectMapper) {
        this.roomLobbyService = roomLobbyService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        roomLobbyService.register(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ClientMessage clientMessage = objectMapper.readValue(message.getPayload(), ClientMessage.class);
        roomLobbyService.handleClientMessage(session, clientMessage);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        roomLobbyService.unregister(session);
    }
}
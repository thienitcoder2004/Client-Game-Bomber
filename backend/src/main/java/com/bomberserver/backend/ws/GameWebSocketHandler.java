package com.bomberserver.backend.ws;

import com.bomberserver.backend.dto.ClientMessage;
import com.bomberserver.backend.dto.ServerMessage;
import com.bomberserver.backend.service.GameRoomService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final GameRoomService gameRoomService;
    private final ObjectMapper objectMapper;

    public GameWebSocketHandler(GameRoomService gameRoomService, ObjectMapper objectMapper) {
        this.gameRoomService = gameRoomService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Integer playerId = gameRoomService.join(session);

        if (playerId == null) {
            session.sendMessage(new TextMessage(
                    objectMapper.writeValueAsString(new ServerMessage("error", "Room full"))
            ));
            session.close();
            return;
        }

        gameRoomService.sendInit(session, playerId);

        Object rawMatchKey = session.getAttributes().get("matchKey");
        if (rawMatchKey instanceof String matchKey) {
            gameRoomService.broadcastState(matchKey);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Object rawPlayerId = session.getAttributes().get("playerId");
        Object rawMatchKey = session.getAttributes().get("matchKey");

        if (!(rawPlayerId instanceof Integer playerId)) return;
        if (!(rawMatchKey instanceof String matchKey)) return;

        ClientMessage clientMessage = objectMapper.readValue(message.getPayload(), ClientMessage.class);
        gameRoomService.handleClientMessage(matchKey, playerId, clientMessage);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object rawPlayerId = session.getAttributes().get("playerId");
        Object rawMatchKey = session.getAttributes().get("matchKey");

        if (rawPlayerId instanceof Integer playerId && rawMatchKey instanceof String matchKey) {
            gameRoomService.leave(matchKey, playerId);
        }
    }
}
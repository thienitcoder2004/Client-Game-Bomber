package com.bomberserver.backend.config;

import com.bomberserver.backend.ws.FriendChatWebSocketHandler;
import com.bomberserver.backend.ws.GameWebSocketHandler;
import com.bomberserver.backend.ws.RoomWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;
    private final RoomWebSocketHandler roomWebSocketHandler;
    private final FriendChatWebSocketHandler friendChatWebSocketHandler;

    @Value("${app.frontend.allowed-origins}")
    private String allowedOriginsProperty;

    public WebSocketConfig(
            GameWebSocketHandler gameWebSocketHandler,
            RoomWebSocketHandler roomWebSocketHandler,
            FriendChatWebSocketHandler friendChatWebSocketHandler
    ) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.roomWebSocketHandler = roomWebSocketHandler;
        this.friendChatWebSocketHandler = friendChatWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);

        registry.addHandler(gameWebSocketHandler, "/ws/game")
                .setAllowedOriginPatterns(allowedOrigins);

        registry.addHandler(roomWebSocketHandler, "/ws/rooms")
                .setAllowedOriginPatterns(allowedOrigins);

        registry.addHandler(friendChatWebSocketHandler, "/ws/friends-chat")
                .setAllowedOriginPatterns(allowedOrigins);
    }
}
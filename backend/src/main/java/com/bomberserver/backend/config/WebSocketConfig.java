package com.bomberserver.backend.config;

import com.example.bomberserver.ws.GameWebSocketHandler;
import com.example.bomberserver.ws.RoomWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;
    private final RoomWebSocketHandler roomWebSocketHandler;

    public WebSocketConfig(
            GameWebSocketHandler gameWebSocketHandler,
            RoomWebSocketHandler roomWebSocketHandler
    ) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.roomWebSocketHandler = roomWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/ws/game")
                .setAllowedOriginPatterns(
                        "http://localhost:3000",
                        "http://127.0.0.1:3000",
                        "http://localhost:5173",
                        "https://client-game-bomber.vercel.app"
                );

        registry.addHandler(roomWebSocketHandler, "/ws/rooms")
                .setAllowedOriginPatterns(
                        "http://localhost:3000",
                        "http://127.0.0.1:3000",
                        "http://localhost:5173",
                        "https://client-game-bomber.vercel.app"
                );
    }
}
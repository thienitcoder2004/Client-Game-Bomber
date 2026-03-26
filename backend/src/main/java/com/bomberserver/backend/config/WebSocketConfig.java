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

/**
 * Class cấu hình toàn bộ WebSocket endpoint cho game.
 *
 * Chức năng:
 * - Đăng ký endpoint websocket cho gameplay
 * - Đăng ký endpoint websocket cho room/lobby
 * - Đăng ký endpoint websocket cho chat bạn bè
 * - Cấu hình allowed origin để frontend có thể kết nối
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /**
     * Handler xử lý gameplay real-time:
     * - di chuyển
     * - đặt bom
     * - dùng item
     * - đồng bộ state game
     */
    private final GameWebSocketHandler gameWebSocketHandler;

    /**
     * Handler xử lý phòng/lobby:
     * - tạo phòng
     * - vào phòng
     * - rời phòng
     * - bắt đầu trận
     * - thêm bot
     */
    private final RoomWebSocketHandler roomWebSocketHandler;

    /**
     * Handler xử lý chat bạn bè real-time.
     */
    private final FriendChatWebSocketHandler friendChatWebSocketHandler;

    /**
     * Danh sách domain frontend được phép kết nối WebSocket.
     * Lấy từ application.properties.
     */
    @Value("${app.frontend.allowed-origins}")
    private String allowedOriginsProperty;

    /**
     * Constructor inject các WebSocket handler cần thiết.
     */
    public WebSocketConfig(
            GameWebSocketHandler gameWebSocketHandler,
            RoomWebSocketHandler roomWebSocketHandler,
            FriendChatWebSocketHandler friendChatWebSocketHandler
    ) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.roomWebSocketHandler = roomWebSocketHandler;
        this.friendChatWebSocketHandler = friendChatWebSocketHandler;
    }

    /**
     * Hàm đăng ký các endpoint WebSocket.
     *
     * @param registry nơi đăng ký các đường dẫn websocket
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Chuyển chuỗi cấu hình origin thành mảng String[]
        String[] allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);

        /**
         * Endpoint gameplay.
         * Frontend sẽ connect tới:
         * ws://.../ws/game
         */
        registry.addHandler(gameWebSocketHandler, "/ws/game")
                .setAllowedOriginPatterns(allowedOrigins);

        /**
         * Endpoint phòng / lobby.
         * Frontend sẽ connect tới:
         * ws://.../ws/rooms
         */
        registry.addHandler(roomWebSocketHandler, "/ws/rooms")
                .setAllowedOriginPatterns(allowedOrigins);

        /**
         * Endpoint chat bạn bè.
         * Frontend sẽ connect tới:
         * ws://.../ws/friends-chat
         */
        registry.addHandler(friendChatWebSocketHandler, "/ws/friends-chat")
                .setAllowedOriginPatterns(allowedOrigins);
    }
}
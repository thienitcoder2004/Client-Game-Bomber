package com.bomberserver.backend.dto.friend;

import java.time.Instant;

/**
 * DTO trả thông tin 1 tin nhắn chat giữa 2 người bạn.
 *
 * Đây là response gửi về frontend để hiển thị lịch sử chat
 * hoặc cập nhật realtime tin nhắn.
 *
 * Dùng record để code ngắn gọn, dễ đọc.
 */
public record FriendChatMessageResponse(

        /**
         * ID của tin nhắn.
         */
        String id,

        /**
         * ID người gửi.
         */
        String senderId,

        /**
         * ID người nhận.
         */
        String receiverId,

        /**
         * Nội dung tin nhắn.
         */
        String content,

        /**
         * Thời điểm gửi tin nhắn.
         */
        Instant createdAt,

        /**
         * Đánh dấu tin nhắn đã bị thu hồi chưa.
         *
         * true  = đã thu hồi
         * false = chưa thu hồi
         */
        boolean recalled,

        /**
         * Thời điểm thu hồi tin nhắn.
         * Nếu chưa thu hồi thì có thể là null.
         */
        Instant recalledAt
) {
}
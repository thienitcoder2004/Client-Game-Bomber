package com.bomberserver.backend.dto.friend;

/**
 * DTO dùng cho message mà frontend gửi lên WebSocket chat bạn bè.
 *
 * Ví dụ các loại message có thể là:
 * - send_message   : gửi tin nhắn
 * - recall_message : thu hồi tin nhắn
 *
 * Class này dùng cho phía client -> server.
 */
public class FriendChatClientMessage {

    /**
     * Loại hành động mà client muốn gửi.
     *
     * Ví dụ:
     * - send_message
     * - recall_message
     */
    public String type;

    /**
     * ID của người bạn đang chat cùng / người nhận tin nhắn.
     */
    public String targetUserId;

    /**
     * Nội dung tin nhắn.
     *
     * Trường này thường dùng khi type là gửi tin nhắn.
     */
    public String content;

    /**
     * ID của tin nhắn.
     *
     * Trường này thường dùng khi type là thu hồi tin nhắn
     * hoặc thao tác trên 1 tin nhắn đã tồn tại.
     */
    public String messageId;
}
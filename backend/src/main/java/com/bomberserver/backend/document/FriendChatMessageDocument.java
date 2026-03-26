package com.bomberserver.backend.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Document lưu từng tin nhắn chat giữa 2 người bạn.
 *
 * Collection trong MongoDB:
 * friend_chat_messages
 *
 * Mỗi document tương ứng với 1 tin nhắn.
 */
@Document("friend_chat_messages")
public class FriendChatMessageDocument {

    /**
     * ID chính của tin nhắn.
     */
    @Id
    private String id;

    /**
     * Khóa hội thoại giữa 2 người dùng.
     *
     * Mục đích:
     * - gom các tin nhắn của cùng 1 cuộc trò chuyện lại với nhau
     * - hỗ trợ query lịch sử chat nhanh hơn
     *
     * Thường conversationKey sẽ được tạo theo kiểu cố định,
     * ví dụ: user nhỏ hơn + "_" + user lớn hơn
     * để tránh 2 chiều tạo ra 2 key khác nhau.
     */
    @Indexed
    private String conversationKey;

    /**
     * ID người gửi tin nhắn.
     */
    private String senderId;

    /**
     * ID người nhận tin nhắn.
     */
    private String receiverId;

    /**
     * Nội dung tin nhắn.
     */
    private String content;

    /**
     * Thời gian gửi tin nhắn.
     */
    private Instant createdAt;

    /**
     * Đánh dấu tin nhắn đã bị thu hồi chưa.
     *
     * true  = đã thu hồi
     * false = chưa thu hồi
     */
    private boolean recalled;

    /**
     * Thời điểm thu hồi tin nhắn.
     * Chỉ có giá trị khi recalled = true.
     */
    private Instant recalledAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationKey() {
        return conversationKey;
    }

    public void setConversationKey(String conversationKey) {
        this.conversationKey = conversationKey;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRecalled() {
        return recalled;
    }

    public void setRecalled(boolean recalled) {
        this.recalled = recalled;
    }

    public Instant getRecalledAt() {
        return recalledAt;
    }

    public void setRecalledAt(Instant recalledAt) {
        this.recalledAt = recalledAt;
    }
}
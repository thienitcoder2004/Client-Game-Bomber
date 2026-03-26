package com.bomberserver.backend.repository;

import com.bomberserver.backend.document.FriendChatMessageDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Repository thao tác với collection friend_chat_messages.
 *
 * Dùng để lưu và truy vấn lịch sử chat giữa 2 người bạn.
 */
public interface FriendChatMessageRepository extends MongoRepository<FriendChatMessageDocument, String> {

    /**
     * Lấy 50 tin nhắn mới nhất của 1 cuộc hội thoại,
     * sắp xếp theo thời gian tạo giảm dần.
     *
     * conversationKey là khóa chung của 2 người chat.
     *
     * @param conversationKey khóa hội thoại
     * @return danh sách tối đa 50 tin nhắn mới nhất
     */
    List<FriendChatMessageDocument> findTop50ByConversationKeyOrderByCreatedAtDesc(String conversationKey);
}
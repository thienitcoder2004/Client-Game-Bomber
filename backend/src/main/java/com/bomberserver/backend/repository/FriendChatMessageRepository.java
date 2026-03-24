package com.bomberserver.backend.repository;

import com.bomberserver.backend.document.FriendChatMessageDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FriendChatMessageRepository extends MongoRepository<FriendChatMessageDocument, String> {
    List<FriendChatMessageDocument> findTop50ByConversationKeyOrderByCreatedAtDesc(String conversationKey);
}
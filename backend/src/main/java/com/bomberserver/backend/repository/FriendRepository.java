package com.bomberserver.backend.repository;

import com.bomberserver.backend.document.FriendDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRepository extends MongoRepository<FriendDocument, String> {
    Optional<FriendDocument> findByUserAIdAndUserBId(String userAId, String userBId);
    List<FriendDocument> findByUserAIdAndStatusOrderByUpdatedAtDesc(String userAId, String status);
    List<FriendDocument> findByUserBIdAndStatusOrderByUpdatedAtDesc(String userBId, String status);
    List<FriendDocument> findByAddresseeIdAndStatusOrderByCreatedAtDesc(String addresseeId, String status);
    List<FriendDocument> findByRequesterIdAndStatusOrderByCreatedAtDesc(String requesterId, String status);
}
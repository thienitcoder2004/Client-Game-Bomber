package com.bomberserver.backend.repository;

import com.example.bomberserver.document.MatchHistoryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MatchHistoryRepository extends MongoRepository<MatchHistoryDocument, String> {
    List<MatchHistoryDocument> findByParticipantUserIdsContainsOrderByEndedAtDesc(String userId);
}
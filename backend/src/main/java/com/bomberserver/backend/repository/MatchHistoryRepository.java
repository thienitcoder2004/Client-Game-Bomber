package com.bomberserver.backend.repository;

import com.bomberserver.backend.document.MatchHistoryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MatchHistoryRepository extends MongoRepository<MatchHistoryDocument, String> {
    List<MatchHistoryDocument> findByParticipantUserIdsContainsOrderByEndedAtDesc(String userId);
}
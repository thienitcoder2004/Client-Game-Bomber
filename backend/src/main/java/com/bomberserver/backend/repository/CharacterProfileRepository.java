package com.bomberserver.backend.repository;

import com.bomberserver.backend.document.CharacterProfileDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CharacterProfileRepository extends MongoRepository<CharacterProfileDocument, String> {
    Optional<CharacterProfileDocument> findByUserId(String userId);
    boolean existsByCharacterNameIgnoreCase(String characterName);
    List<CharacterProfileDocument> findTop20ByCharacterNameContainingIgnoreCase(String characterName);
}
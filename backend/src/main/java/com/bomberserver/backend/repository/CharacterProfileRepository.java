package com.bomberserver.backend.repository;

import com.example.bomberserver.document.CharacterProfileDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CharacterProfileRepository extends MongoRepository<CharacterProfileDocument, String> {
    Optional<CharacterProfileDocument> findByUserId(String userId);
    boolean existsByCharacterNameIgnoreCase(String characterName);
}
package com.bomberserver.backend.repository;

import com.example.bomberserver.document.UserAccountDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserAccountRepository extends MongoRepository<UserAccountDocument, String> {
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);

    Optional<UserAccountDocument> findByEmailIgnoreCase(String email);
    Optional<UserAccountDocument> findByUsernameIgnoreCase(String username);
}
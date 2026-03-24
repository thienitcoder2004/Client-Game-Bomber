package com.bomberserver.backend.repository;

import com.bomberserver.backend.document.UserAccountDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends MongoRepository<UserAccountDocument, String> {
    Optional<UserAccountDocument> findByEmailIgnoreCase(String email);
    Optional<UserAccountDocument> findByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);

    List<UserAccountDocument> findTop20ByUsernameContainingIgnoreCase(String username);
}
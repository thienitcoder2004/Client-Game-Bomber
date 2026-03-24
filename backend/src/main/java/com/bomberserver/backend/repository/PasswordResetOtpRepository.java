package com.bomberserver.backend.repository;

import com.bomberserver.backend.document.PasswordResetOtpDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetOtpRepository extends MongoRepository<PasswordResetOtpDocument, String> {
    Optional<PasswordResetOtpDocument> findTopByEmailIgnoreCaseOrderByCreatedAtDesc(String email);
    Optional<PasswordResetOtpDocument> findTopByEmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc(String email);
    List<PasswordResetOtpDocument> findByEmailIgnoreCaseAndUsedFalse(String email);
}
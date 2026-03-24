package com.bomberserver.backend.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Lưu mã OTP quên mật khẩu gửi qua email.
// expiresAt có TTL index nên Mongo sẽ tự xóa record hết hạn.
@Document("password_reset_otps")
public class PasswordResetOtpDocument {

    @Id
    private String id;

    private String userId;
    private String email;
    private String otpCodeHash;
    private Boolean used = false;

    @Indexed(name = "password_reset_otp_expires_idx", expireAfterSeconds = 0)
    private Instant expiresAt;

    private Instant createdAt;
    private Instant usedAt;

    public PasswordResetOtpDocument() {
    }

    public PasswordResetOtpDocument(String userId, String email, String otpCodeHash, Instant expiresAt) {
        this.userId = userId;
        this.email = email;
        this.otpCodeHash = otpCodeHash;
        this.expiresAt = expiresAt;
        this.used = false;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getOtpCodeHash() {
        return otpCodeHash;
    }

    public Boolean getUsed() {
        return used;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setOtpCodeHash(String otpCodeHash) {
        this.otpCodeHash = otpCodeHash;
    }

    public void setUsed(Boolean used) {
        this.used = used;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }
}
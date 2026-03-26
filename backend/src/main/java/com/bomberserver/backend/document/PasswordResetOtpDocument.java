package com.bomberserver.backend.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Document lưu mã OTP dùng cho chức năng quên mật khẩu.
 *
 * Collection trong MongoDB:
 * password_reset_otps
 *
 * Mỗi document đại diện cho 1 lần tạo OTP reset password.
 */
@Document("password_reset_otps")
public class PasswordResetOtpDocument {

    /**
     * ID chính của document.
     */
    @Id
    private String id;

    /**
     * ID người dùng yêu cầu reset mật khẩu.
     */
    private String userId;

    /**
     * Email nhận OTP.
     */
    private String email;

    /**
     * Mã OTP đã được hash trước khi lưu.
     *
     * Không nên lưu OTP thô để đảm bảo an toàn.
     */
    private String otpCodeHash;

    /**
     * Đánh dấu OTP đã được dùng chưa.
     *
     * false = chưa dùng
     * true  = đã dùng
     */
    private Boolean used = false;

    /**
     * Thời điểm OTP hết hạn.
     *
     * Có TTL index nên khi hết hạn,
     * MongoDB sẽ tự động xóa document này.
     *
     * expireAfterSeconds = 0 nghĩa là
     * document sẽ hết hạn đúng tại thời điểm expiresAt.
     */
    @Indexed(name = "password_reset_otp_expires_idx", expireAfterSeconds = 0)
    private Instant expiresAt;

    /**
     * Thời điểm tạo OTP.
     */
    private Instant createdAt;

    /**
     * Thời điểm OTP được sử dụng.
     * Chỉ có giá trị khi used = true.
     */
    private Instant usedAt;

    /**
     * Constructor rỗng để Spring / Mongo mapping.
     */
    public PasswordResetOtpDocument() {
    }

    /**
     * Constructor tạo OTP document mới.
     *
     * @param userId id người dùng
     * @param email email reset mật khẩu
     * @param otpCodeHash mã OTP đã hash
     * @param expiresAt thời điểm hết hạn OTP
     */
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
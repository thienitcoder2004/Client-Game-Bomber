package com.bomberserver.backend.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Document lưu tài khoản người dùng.
 *
 * Collection trong MongoDB:
 * users
 *
 * Đây là bảng chính để quản lý:
 * - email
 * - username
 * - password đã hash
 * - role
 * - trạng thái active
 */
@Document("users")
public class UserAccountDocument {

    /**
     * ID chính của tài khoản.
     */
    @Id
    private String id;

    /**
     * Email của user.
     *
     * Đặt unique = true để không bị trùng email.
     */
    @Indexed(unique = true)
    private String email;

    /**
     * Username của user.
     *
     * Đặt unique = true để không bị trùng username.
     */
    @Indexed(unique = true)
    private String username;

    /**
     * Mật khẩu đã được hash.
     *
     * Không lưu password thô.
     */
    private String passwordHash;

    /**
     * Vai trò của user.
     *
     * Ví dụ:
     * - USER
     * - ADMIN
     */
    private String role = "USER";

    /**
     * Trạng thái hoạt động của tài khoản.
     *
     * true  = đang hoạt động
     * false = bị khóa / vô hiệu hóa
     */
    private Boolean active = true;

    /**
     * Thời điểm tạo tài khoản.
     */
    private Instant createdAt;

    /**
     * Constructor rỗng để Spring / Mongo mapping.
     */
    public UserAccountDocument() {
    }

    /**
     * Constructor tạo tài khoản mới.
     *
     * Mặc định:
     * - role = USER
     * - active = true
     * - createdAt = hiện tại
     *
     * @param email email người dùng
     * @param username tên đăng nhập
     * @param passwordHash mật khẩu đã hash
     */
    public UserAccountDocument(String email, String username, String passwordHash) {
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = "USER";
        this.active = true;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public Boolean getActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
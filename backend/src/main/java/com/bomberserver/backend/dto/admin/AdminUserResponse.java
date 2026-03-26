package com.bomberserver.backend.dto.admin;

import java.time.Instant;

/**
 * DTO trả dữ liệu thông tin user cho trang quản trị ADMIN.
 *
 * Dùng để admin xem danh sách tài khoản người chơi.
 */
public class AdminUserResponse {

    /**
     * ID của user.
     */
    public String id;

    /**
     * Email của user.
     */
    public String email;

    /**
     * Username đăng nhập của user.
     */
    public String username;

    /**
     * Tên nhân vật trong game.
     */
    public String characterName;

    /**
     * Giới tính nhân vật.
     */
    public String gender;

    /**
     * Vai trò của tài khoản.
     *
     * Ví dụ:
     * - USER
     * - ADMIN
     */
    public String role;

    /**
     * Trạng thái hoạt động của tài khoản.
     *
     * true  = đang hoạt động
     * false = đã bị khóa
     */
    public boolean active;

    /**
     * Thời điểm tạo tài khoản.
     */
    public Instant createdAt;

    /**
     * Constructor rỗng.
     */
    public AdminUserResponse() {
    }

    /**
     * Constructor đầy đủ để tạo response nhanh.
     *
     * @param id id user
     * @param email email user
     * @param username username
     * @param characterName tên nhân vật
     * @param gender giới tính
     * @param role vai trò
     * @param active trạng thái tài khoản
     * @param createdAt thời điểm tạo
     */
    public AdminUserResponse(
            String id,
            String email,
            String username,
            String characterName,
            String gender,
            String role,
            boolean active,
            Instant createdAt
    ) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.characterName = characterName;
        this.gender = gender;
        this.role = role;
        this.active = active;
        this.createdAt = createdAt;
    }
}
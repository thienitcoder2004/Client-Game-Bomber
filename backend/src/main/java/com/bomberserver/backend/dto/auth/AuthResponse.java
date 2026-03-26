package com.bomberserver.backend.dto.auth;

/**
 * DTO trả về sau khi đăng nhập hoặc đăng ký thành công.
 *
 * Dùng để frontend nhận:
 * - token JWT
 * - thông tin cơ bản của tài khoản
 * - trạng thái profile đã hoàn thiện chưa
 * - role của user
 */
public class AuthResponse {

    /**
     * JWT token để frontend lưu và dùng cho các request sau.
     */
    public String token;

    /**
     * ID của user.
     */
    public String userId;

    /**
     * Email của user.
     */
    public String email;

    /**
     * Username đăng nhập.
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
     * Mã avatar để frontend biết dùng bộ ảnh nào.
     */
    public String avatarCode;

    /**
     * Đánh dấu profile nhân vật đã hoàn thiện chưa.
     *
     * true  = đã điền đủ
     * false = còn thiếu thông tin
     */
    public boolean profileCompleted;

    /**
     * Vai trò tài khoản.
     *
     * Ví dụ:
     * - USER
     * - ADMIN
     */
    public String role;

    /**
     * Constructor rỗng.
     */
    public AuthResponse() {
    }

    /**
     * Constructor đầy đủ.
     *
     * @param token JWT token
     * @param userId id user
     * @param email email
     * @param username username
     * @param characterName tên nhân vật
     * @param gender giới tính
     * @param avatarCode mã avatar
     * @param profileCompleted trạng thái hoàn thiện profile
     * @param role vai trò tài khoản
     */
    public AuthResponse(
            String token,
            String userId,
            String email,
            String username,
            String characterName,
            String gender,
            String avatarCode,
            boolean profileCompleted,
            String role
    ) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.username = username;
        this.characterName = characterName;
        this.gender = gender;
        this.avatarCode = avatarCode;
        this.profileCompleted = profileCompleted;
        this.role = role;
    }
}
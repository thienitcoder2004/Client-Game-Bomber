package com.bomberserver.backend.dto.profile;

/**
 * DTO trả về thông tin hồ sơ cá nhân của user.
 *
 * Dùng cho:
 * - API lấy profile của chính mình
 * - API cập nhật profile xong trả dữ liệu mới về frontend
 */
public class ProfileResponse {

    /**
     * ID của user.
     */
    public String userId;

    /**
     * Email tài khoản.
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
     *
     * Ví dụ:
     * - MALE
     * - FEMALE
     */
    public String gender;

    /**
     * Mã avatar nhân vật.
     */
    public String avatarCode;

    /**
     * Đánh dấu profile đã hoàn thiện chưa.
     *
     * true  = đã điền đủ thông tin
     * false = còn thiếu
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
    public ProfileResponse() {
    }

    /**
     * Constructor đầy đủ để tạo response nhanh.
     *
     * @param userId id user
     * @param email email
     * @param username username
     * @param characterName tên nhân vật
     * @param gender giới tính
     * @param avatarCode mã avatar
     * @param profileCompleted trạng thái hoàn tất profile
     * @param role vai trò tài khoản
     */
    public ProfileResponse(
            String userId,
            String email,
            String username,
            String characterName,
            String gender,
            String avatarCode,
            boolean profileCompleted,
            String role
    ) {
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
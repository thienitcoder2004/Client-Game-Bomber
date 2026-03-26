package com.bomberserver.backend.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Document lưu hồ sơ nhân vật của người chơi.
 *
 * Collection trong MongoDB:
 * character_profiles
 *
 * Mỗi user sẽ có đúng 1 profile nhân vật.
 * Profile này dùng để lưu các thông tin hiển thị trong game như:
 * - tên nhân vật
 * - giới tính
 * - mã avatar
 */
@Document("character_profiles")
public class CharacterProfileDocument {

    /**
     * ID chính của document trong MongoDB.
     */
    @Id
    private String id;

    /**
     * ID của user sở hữu profile này.
     *
     * Đặt unique = true để đảm bảo:
     * 1 user chỉ có đúng 1 profile.
     */
    @Indexed(unique = true)
    private String userId;

    /**
     * Tên nhân vật hiển thị trong game.
     */
    private String characterName;

    /**
     * Giới tính nhân vật.
     *
     * Quy ước hiện tại:
     * - MALE
     * - FEMALE
     */
    private String gender;

    /**
     * Mã avatar để frontend biết dùng bộ ảnh nào.
     *
     * Ví dụ:
     * - male_01
     * - female_01
     */
    private String avatarCode;

    /**
     * Thời điểm tạo profile.
     */
    private Instant createdAt;

    /**
     * Thời điểm cập nhật profile gần nhất.
     */
    private Instant updatedAt;

    /**
     * Constructor rỗng để Spring / MongoDB mapping dữ liệu.
     */
    public CharacterProfileDocument() {
    }

    /**
     * Constructor tạo profile mới theo userId.
     *
     * Khi mới tạo:
     * - characterName rỗng
     * - gender rỗng
     * - avatarCode rỗng
     * - createdAt / updatedAt là thời điểm hiện tại
     *
     * @param userId id người dùng
     */
    public CharacterProfileDocument(String userId) {
        this.userId = userId;
        this.characterName = "";
        this.gender = "";
        this.avatarCode = "";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Kiểm tra profile đã hoàn tất chưa.
     *
     * Điều kiện hoàn tất:
     * - có characterName
     * - có gender
     * - có avatarCode
     *
     * @return true nếu profile đã đầy đủ, false nếu còn thiếu
     */
    public boolean isProfileCompleted() {
        return characterName != null && !characterName.isBlank()
                && gender != null && !gender.isBlank()
                && avatarCode != null && !avatarCode.isBlank();
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getCharacterName() {
        return characterName;
    }

    public String getGender() {
        return gender;
    }

    public String getAvatarCode() {
        return avatarCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public void setAvatarCode(String avatarCode) {
        this.avatarCode = avatarCode;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
package com.bomberserver.backend.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Profile nhân vật gắn với user
@Document("character_profiles")
public class CharacterProfileDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private String characterName;

    // MALE / FEMALE
    private String gender;

    // mã avatar để frontend biết dùng bộ ảnh nào
    private String avatarCode;

    private Instant createdAt;
    private Instant updatedAt;

    public CharacterProfileDocument() {
    }

    public CharacterProfileDocument(String userId) {
        this.userId = userId;
        this.characterName = "";
        this.gender = "";
        this.avatarCode = "";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

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
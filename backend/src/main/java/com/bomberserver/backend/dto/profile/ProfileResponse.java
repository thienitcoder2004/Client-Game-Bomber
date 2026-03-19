package com.bomberserver.backend.dto.profile;

public class ProfileResponse {
    public String userId;
    public String email;
    public String username;
    public String characterName;
    public String gender;
    public String avatarCode;
    public boolean profileCompleted;

    public ProfileResponse() {
    }

    public ProfileResponse(
            String userId,
            String email,
            String username,
            String characterName,
            String gender,
            String avatarCode,
            boolean profileCompleted
    ) {
        this.userId = userId;
        this.email = email;
        this.username = username;
        this.characterName = characterName;
        this.gender = gender;
        this.avatarCode = avatarCode;
        this.profileCompleted = profileCompleted;
    }
}
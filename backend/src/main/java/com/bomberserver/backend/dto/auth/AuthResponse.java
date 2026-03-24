package com.bomberserver.backend.dto.auth;

// Response trả token + thông tin cơ bản cho frontend
public class AuthResponse {
    public String token;
    public String userId;
    public String email;
    public String username;
    public String characterName;
    public String gender;
    public String avatarCode;
    public boolean profileCompleted;
    public String role;

    public AuthResponse() {
    }

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
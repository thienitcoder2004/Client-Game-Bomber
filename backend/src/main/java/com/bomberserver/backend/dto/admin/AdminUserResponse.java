package com.bomberserver.backend.dto.admin;

import java.time.Instant;

public class AdminUserResponse {
    public String id;
    public String email;
    public String username;
    public String characterName;
    public String gender;
    public String role;
    public boolean active;
    public Instant createdAt;

    public AdminUserResponse() {
    }

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
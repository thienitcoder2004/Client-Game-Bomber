package com.bomberserver.backend.dto.friend;

import jakarta.validation.constraints.NotBlank;

public class CreateFriendRequest {

    @NotBlank(message = "Thiếu user cần kết bạn")
    public String targetUserId;
}
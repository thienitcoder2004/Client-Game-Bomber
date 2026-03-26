package com.bomberserver.backend.dto.friend;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO dùng khi frontend gửi yêu cầu kết bạn.
 *
 * API sẽ nhận vào id của user mục tiêu
 * mà người dùng hiện tại muốn gửi lời mời kết bạn tới.
 */
public class CreateFriendRequest {

    /**
     * ID của user cần kết bạn.
     *
     * Không được để trống.
     */
    @NotBlank(message = "Thiếu user cần kết bạn")
    public String targetUserId;
}
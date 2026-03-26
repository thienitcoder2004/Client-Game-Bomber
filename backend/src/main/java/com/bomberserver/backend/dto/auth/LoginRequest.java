package com.bomberserver.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO nhận dữ liệu đăng nhập từ frontend.
 *
 * User có thể đăng nhập bằng:
 * - email
 * hoặc
 * - username
 */
public class LoginRequest {

    /**
     * Giá trị đăng nhập.
     *
     * Có thể là:
     * - email
     * - username
     */
    @NotBlank(message = "Email hoặc username không được để trống")
    public String login;

    /**
     * Mật khẩu đăng nhập.
     */
    @NotBlank(message = "Mật khẩu không được để trống")
    public String password;
}
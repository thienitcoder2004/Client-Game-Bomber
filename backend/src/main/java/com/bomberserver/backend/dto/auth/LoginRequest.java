package com.bomberserver.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

// login có thể nhập email hoặc username
public class LoginRequest {

    @NotBlank(message = "Email hoặc username không được để trống")
    public String login;

    @NotBlank(message = "Mật khẩu không được để trống")
    public String password;
}
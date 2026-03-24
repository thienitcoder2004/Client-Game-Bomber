package com.bomberserver.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "Email hoặc username không được để trống")
    public String login;

    @NotBlank(message = "Mật khẩu không được để trống")
    public String password;
}
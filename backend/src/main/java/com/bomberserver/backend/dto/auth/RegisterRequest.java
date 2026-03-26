package com.bomberserver.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO nhận dữ liệu đăng ký tài khoản mới.
 *
 * Frontend sẽ gửi:
 * - email
 * - username
 * - password
 */
public class RegisterRequest {

    /**
     * Email tài khoản.
     *
     * Phải đúng định dạng email và không được để trống.
     */
    @Email(message = "Email không hợp lệ")
    @NotBlank(message = "Email không được để trống")
    public String email;

    /**
     * Username đăng nhập.
     *
     * Yêu cầu:
     * - không để trống
     * - từ 3 đến 30 ký tự
     */
    @NotBlank(message = "Username không được để trống")
    @Size(min = 3, max = 30, message = "Username phải từ 3 đến 30 ký tự")
    public String username;

    /**
     * Mật khẩu đăng ký.
     *
     * Yêu cầu:
     * - không để trống
     * - tối thiểu 6 ký tự
     */
    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, max = 100, message = "Mật khẩu phải từ 6 ký tự")
    public String password;
}
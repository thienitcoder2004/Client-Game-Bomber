package com.bomberserver.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO nhận dữ liệu yêu cầu quên mật khẩu.
 *
 * Frontend gửi email lên để backend:
 * - kiểm tra tài khoản có tồn tại không
 * - tạo OTP
 * - gửi mail reset password
 */
public class ForgotPasswordRequest {

    /**
     * Email người dùng dùng để yêu cầu quên mật khẩu.
     *
     * @Email    : bắt buộc đúng định dạng email
     * @NotBlank : không được để trống
     */
    @Email(message = "Email không hợp lệ")
    @NotBlank(message = "Email không được để trống")
    public String email;
}
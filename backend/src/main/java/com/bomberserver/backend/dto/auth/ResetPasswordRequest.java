package com.bomberserver.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO nhận dữ liệu đặt lại mật khẩu.
 *
 * Frontend gửi lên:
 * - email
 * - mã OTP
 * - mật khẩu mới
 */
public class ResetPasswordRequest {

    /**
     * Email của tài khoản cần đặt lại mật khẩu.
     */
    @Email(message = "Email không hợp lệ")
    @NotBlank(message = "Email không được để trống")
    public String email;

    /**
     * Mã OTP người dùng nhận được qua email.
     *
     * Yêu cầu đúng 6 ký tự.
     */
    @NotBlank(message = "Mã OTP không được để trống")
    @Size(min = 6, max = 6, message = "Mã OTP phải gồm 6 số")
    public String otpCode;

    /**
     * Mật khẩu mới sau khi xác thực OTP thành công.
     *
     * Yêu cầu tối thiểu 6 ký tự.
     */
    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 6, max = 100, message = "Mật khẩu mới phải từ 6 ký tự")
    public String newPassword;
}
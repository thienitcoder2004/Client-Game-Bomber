package com.bomberserver.backend.controller;

import com.bomberserver.backend.dto.auth.AuthResponse;
import com.bomberserver.backend.dto.auth.ForgotPasswordRequest;
import com.bomberserver.backend.dto.auth.LoginRequest;
import com.bomberserver.backend.dto.auth.RegisterRequest;
import com.bomberserver.backend.dto.auth.ResetPasswordRequest;
import com.bomberserver.backend.dto.common.MessageResponse;
import com.bomberserver.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller xử lý các chức năng xác thực tài khoản.
 *
 * Chức năng chính:
 * - Đăng ký
 * - Đăng nhập
 * - Gửi yêu cầu quên mật khẩu
 * - Đặt lại mật khẩu
 *
 * Base URL:
 * /api/auth
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Service xử lý logic liên quan đến xác thực.
     */
    private final AuthService authService;

    /**
     * Constructor inject AuthService.
     *
     * @param authService service xử lý auth
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * API đăng ký tài khoản mới.
     *
     * Endpoint:
     * POST /api/auth/register
     *
     * @param request dữ liệu đăng ký từ client
     * @return AuthResponse nếu thành công, hoặc message lỗi nếu thất bại
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            // Gọi service để đăng ký tài khoản
            AuthResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            // Nếu có lỗi validate/nghiệp vụ thì trả về 400
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * API đăng nhập.
     *
     * Endpoint:
     * POST /api/auth/login
     *
     * @param request dữ liệu đăng nhập
     * @return AuthResponse chứa token và thông tin user nếu thành công
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            // Gọi service đăng nhập
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            // Sai tài khoản, mật khẩu hoặc lỗi nghiệp vụ
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * API gửi yêu cầu quên mật khẩu.
     *
     * Endpoint:
     * POST /api/auth/forgot-password/request
     *
     * Thường dùng để:
     * - kiểm tra email tồn tại
     * - tạo mã OTP / token reset
     * - gửi mail cho người dùng
     *
     * @param request dữ liệu yêu cầu quên mật khẩu
     * @return message phản hồi
     */
    @PostMapping("/forgot-password/request")
    public ResponseEntity<?> requestForgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            // Gọi service xử lý yêu cầu quên mật khẩu
            MessageResponse response = authService.requestForgotPassword(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * API đặt lại mật khẩu sau khi đã xác minh yêu cầu quên mật khẩu.
     *
     * Endpoint:
     * POST /api/auth/forgot-password/reset
     *
     * @param request dữ liệu reset mật khẩu
     * @return message thành công hoặc message lỗi
     */
    @PostMapping("/forgot-password/reset")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            // Gọi service để đổi mật khẩu mới
            MessageResponse response = authService.resetPassword(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
package com.bomberserver.backend.controller;

import com.bomberserver.backend.dto.profile.ProfileResponse;
import com.bomberserver.backend.dto.profile.UpdateProfileRequest;
import com.bomberserver.backend.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller xử lý thông tin hồ sơ cá nhân của người chơi.
 *
 * Chức năng:
 * - Xem hồ sơ của chính mình
 * - Cập nhật hồ sơ của chính mình
 *
 * Base URL:
 * /api/profile
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    /**
     * Service xử lý thông tin profile.
     */
    private final ProfileService profileService;

    /**
     * Constructor inject ProfileService.
     *
     * @param profileService service xử lý profile
     */
    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * API lấy thông tin hồ sơ của user đang đăng nhập.
     *
     * Endpoint:
     * GET /api/profile/me
     *
     * @param authentication thông tin user hiện tại
     * @return ProfileResponse hoặc message lỗi
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        try {
            // Lấy userId từ token
            String userId = (String) authentication.getPrincipal();

            // Gọi service lấy profile
            ProfileResponse response = profileService.getMyProfile(userId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * API cập nhật hồ sơ của user đang đăng nhập.
     *
     * Endpoint:
     * PUT /api/profile/me
     *
     * @param authentication thông tin user hiện tại
     * @param request dữ liệu profile cần cập nhật
     * @return ProfileResponse sau khi cập nhật hoặc message lỗi
     */
    @PutMapping("/me")
    public ResponseEntity<?> updateMe(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        try {
            // Lấy userId từ token
            String userId = (String) authentication.getPrincipal();

            // Gọi service cập nhật profile
            ProfileResponse response = profileService.updateMyProfile(userId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
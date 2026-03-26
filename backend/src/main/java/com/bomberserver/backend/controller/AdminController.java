package com.bomberserver.backend.controller;

import com.bomberserver.backend.dto.admin.AdminMatchResponse;
import com.bomberserver.backend.dto.admin.AdminUserResponse;
import com.bomberserver.backend.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller dành riêng cho ADMIN.
 *
 * Chức năng chính:
 * - Xem danh sách người dùng
 * - Khóa tài khoản người dùng
 * - Mở khóa tài khoản người dùng
 * - Xóa tài khoản người dùng
 * - Xem danh sách lịch sử các trận đấu
 *
 * Base URL:
 * /api/admin
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    /**
     * Service xử lý logic nghiệp vụ cho admin.
     */
    private final AdminService adminService;

    /**
     * Constructor inject AdminService.
     *
     * @param adminService service xử lý chức năng admin
     */
    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * API lấy toàn bộ danh sách người dùng.
     *
     * Endpoint:
     * GET /api/admin/users
     *
     * Kết quả:
     * - Thành công: trả về list AdminUserResponse
     * - Thất bại: trả về message lỗi
     *
     * @return danh sách user hoặc message lỗi
     */
    @GetMapping("/users")
    public ResponseEntity<?> getUsers() {
        try {
            List<AdminUserResponse> users = adminService.getUsers();
            return ResponseEntity.ok(users);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * API khóa tài khoản của 1 user.
     *
     * Endpoint:
     * PATCH /api/admin/users/{userId}/lock
     *
     * Cách hoạt động:
     * - Lấy id admin hiện tại từ Authentication
     * - Gọi service để khóa tài khoản user theo userId
     *
     * @param userId id của user cần khóa
     * @param authentication thông tin người đang đăng nhập
     * @return thông tin user sau khi bị khóa hoặc message lỗi
     */
    @PatchMapping("/users/{userId}/lock")
    public ResponseEntity<?> lockUser(@PathVariable String userId, Authentication authentication) {
        try {
            // Lấy id admin hiện tại từ token đã xác thực
            String currentAdminId = (String) authentication.getPrincipal();

            // Gọi service để khóa user
            AdminUserResponse user = adminService.lockUser(userId, currentAdminId);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * API mở khóa tài khoản của 1 user.
     *
     * Endpoint:
     * PATCH /api/admin/users/{userId}/unlock
     *
     * Cách hoạt động:
     * - Lấy id admin hiện tại từ Authentication
     * - Gọi service để mở khóa tài khoản user theo userId
     *
     * @param userId id của user cần mở khóa
     * @param authentication thông tin người đang đăng nhập
     * @return thông tin user sau khi mở khóa hoặc message lỗi
     */
    @PatchMapping("/users/{userId}/unlock")
    public ResponseEntity<?> unlockUser(@PathVariable String userId, Authentication authentication) {
        try {
            // Lấy id admin hiện tại
            String currentAdminId = (String) authentication.getPrincipal();

            // Gọi service để mở khóa user
            AdminUserResponse user = adminService.unlockUser(userId, currentAdminId);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * API xóa tài khoản user.
     *
     * Endpoint:
     * DELETE /api/admin/users/{userId}
     *
     * Cách hoạt động:
     * - Lấy id admin hiện tại
     * - Gọi service xóa tài khoản của user
     *
     * @param userId id của user cần xóa
     * @param authentication thông tin người đang đăng nhập
     * @return message thành công hoặc message lỗi
     */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable String userId, Authentication authentication) {
        try {
            // Lấy id admin hiện tại
            String currentAdminId = (String) authentication.getPrincipal();

            // Gọi service xóa user
            adminService.deleteUser(userId, currentAdminId);

            return ResponseEntity.ok(Map.of("message", "Xóa tài khoản thành công"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * API lấy danh sách toàn bộ lịch sử trận đấu cho admin xem.
     *
     * Endpoint:
     * GET /api/admin/matches
     *
     * @return danh sách trận đấu hoặc message lỗi
     */
    @GetMapping("/matches")
    public ResponseEntity<?> getMatches() {
        try {
            List<AdminMatchResponse> matches = adminService.getMatches();
            return ResponseEntity.ok(matches);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
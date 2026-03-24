package com.bomberserver.backend.controller;

import com.bomberserver.backend.dto.admin.AdminMatchResponse;
import com.bomberserver.backend.dto.admin.AdminUserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.bomberserver.backend.service.AdminService;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public ResponseEntity<?> getUsers() {
        try {
            List<AdminUserResponse> users = adminService.getUsers();
            return ResponseEntity.ok(users);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PatchMapping("/users/{userId}/lock")
    public ResponseEntity<?> lockUser(@PathVariable String userId, Authentication authentication) {
        try {
            String currentAdminId = (String) authentication.getPrincipal();
            AdminUserResponse user = adminService.lockUser(userId, currentAdminId);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PatchMapping("/users/{userId}/unlock")
    public ResponseEntity<?> unlockUser(@PathVariable String userId) {
        try {
            AdminUserResponse user = adminService.unlockUser(userId);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

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
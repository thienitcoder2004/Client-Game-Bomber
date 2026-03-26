package com.bomberserver.backend.controller;

import com.bomberserver.backend.document.MatchHistoryDocument;
import com.bomberserver.backend.service.HistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller xử lý lịch sử đấu của người dùng.
 *
 * Chức năng:
 * - Lấy lịch sử trận đấu của chính tài khoản đang đăng nhập
 *
 * Base URL:
 * /api/history
 */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    /**
     * Service xử lý lịch sử trận đấu.
     */
    private final HistoryService historyService;

    /**
     * Constructor inject HistoryService.
     *
     * @param historyService service xử lý history
     */
    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    /**
     * API lấy lịch sử trận đấu của người dùng hiện tại.
     *
     * Endpoint:
     * GET /api/history/me
     *
     * Cách hoạt động:
     * - Lấy userId từ Authentication
     * - Gọi service lấy danh sách match history theo userId
     *
     * @param authentication thông tin người đang đăng nhập
     * @return danh sách lịch sử trận đấu
     */
    @GetMapping("/me")
    public ResponseEntity<List<MatchHistoryDocument>> myHistory(Authentication authentication) {
        // Lấy userId hiện tại từ token
        String userId = (String) authentication.getPrincipal();

        // Trả về lịch sử trận đấu của user
        return ResponseEntity.ok(historyService.getMyHistory(userId));
    }
}
package com.bomberserver.backend.controller;

import com.bomberserver.backend.dto.common.MessageResponse;
import com.bomberserver.backend.dto.friend.CreateFriendRequest;
import com.bomberserver.backend.dto.friend.FriendChatMessageResponse;
import com.bomberserver.backend.dto.friend.FriendListItemResponse;
import com.bomberserver.backend.dto.friend.FriendRequestResponse;
import com.bomberserver.backend.dto.friend.FriendSearchItemResponse;
import com.bomberserver.backend.service.FriendService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller xử lý toàn bộ chức năng bạn bè và chat bạn bè.
 *
 * Chức năng:
 * - Tìm kiếm người dùng để kết bạn
 * - Lấy danh sách bạn bè
 * - Lấy danh sách lời mời kết bạn đến
 * - Lấy danh sách lời mời kết bạn đã gửi
 * - Gửi lời mời kết bạn
 * - Chấp nhận lời mời kết bạn
 * - Từ chối lời mời kết bạn
 * - Xóa bạn bè
 * - Lấy lịch sử chat với bạn bè
 *
 * Base URL:
 * /api/friends
 */
@RestController
@RequestMapping("/api/friends")
public class FriendController {

    /**
     * Service xử lý logic bạn bè và chat.
     */
    private final FriendService friendService;

    /**
     * Constructor inject FriendService.
     *
     * @param friendService service xử lý friend
     */
    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    /**
     * API tìm kiếm người dùng để kết bạn.
     *
     * Endpoint:
     * GET /api/friends/search?keyword=abc
     *
     * Cách hoạt động:
     * - Lấy userId hiện tại
     * - Tìm các user theo từ khóa keyword
     * - Có thể loại bỏ chính mình hoặc các user đã là bạn tùy logic service
     *
     * @param authentication thông tin user hiện tại
     * @param keyword từ khóa tìm kiếm
     * @return danh sách user phù hợp hoặc message lỗi
     */
    @GetMapping("/search")
    public ResponseEntity<?> search(Authentication authentication, @RequestParam(defaultValue = "") String keyword) {
        try {
            // Lấy id user hiện tại
            String userId = (String) authentication.getPrincipal();

            // Gọi service tìm user theo keyword
            List<FriendSearchItemResponse> response = friendService.searchUsers(userId, keyword);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * API lấy danh sách bạn bè hiện tại.
     *
     * Endpoint:
     * GET /api/friends
     *
     * @param authentication thông tin user hiện tại
     * @return danh sách bạn bè hoặc message lỗi
     */
    @GetMapping
    public ResponseEntity<?> getFriends(Authentication authentication) {
        try {
            // Lấy userId hiện tại
            String userId = (String) authentication.getPrincipal();

            // Gọi service lấy danh sách bạn bè
            List<FriendListItemResponse> response = friendService.getFriends(userId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * API lấy danh sách lời mời kết bạn gửi đến cho user hiện tại.
     *
     * Endpoint:
     * GET /api/friends/requests/incoming
     *
     * @param authentication thông tin user hiện tại
     * @return danh sách lời mời đến hoặc message lỗi
     */
    @GetMapping("/requests/incoming")
    public ResponseEntity<?> getIncomingRequests(Authentication authentication) {
        try {
            // Lấy userId hiện tại
            String userId = (String) authentication.getPrincipal();

            // Gọi service lấy lời mời đến
            List<FriendRequestResponse> response = friendService.getIncomingRequests(userId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * API lấy danh sách lời mời kết bạn mà user hiện tại đã gửi đi.
     *
     * Endpoint:
     * GET /api/friends/requests/outgoing
     *
     * @param authentication thông tin user hiện tại
     * @return danh sách lời mời đã gửi hoặc message lỗi
     */
    @GetMapping("/requests/outgoing")
    public ResponseEntity<?> getOutgoingRequests(Authentication authentication) {
        try {
            // Lấy userId hiện tại
            String userId = (String) authentication.getPrincipal();

            // Gọi service lấy lời mời đã gửi
            List<FriendRequestResponse> response = friendService.getOutgoingRequests(userId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * API gửi lời mời kết bạn tới user khác.
     *
     * Endpoint:
     * POST /api/friends/requests
     *
     * Body ví dụ:
     * {
     *   "targetUserId": "abc123"
     * }
     *
     * @param authentication thông tin user hiện tại
     * @param request dữ liệu chứa id người muốn kết bạn
     * @return message phản hồi thành công hoặc lỗi
     */
    @PostMapping("/requests")
    public ResponseEntity<?> sendRequest(Authentication authentication, @Valid @RequestBody CreateFriendRequest request) {
        try {
            // Lấy userId người gửi lời mời
            String userId = (String) authentication.getPrincipal();

            // Gọi service gửi lời mời kết bạn
            MessageResponse response = friendService.sendFriendRequest(userId, request.targetUserId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * API chấp nhận lời mời kết bạn.
     *
     * Endpoint:
     * POST /api/friends/requests/{requestId}/accept
     *
     * @param authentication thông tin user hiện tại
     * @param requestId id của lời mời kết bạn
     * @return message thành công hoặc lỗi
     */
    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<?> acceptRequest(Authentication authentication, @PathVariable String requestId) {
        try {
            // Lấy userId hiện tại
            String userId = (String) authentication.getPrincipal();

            // Gọi service chấp nhận lời mời
            MessageResponse response = friendService.acceptRequest(userId, requestId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * API từ chối lời mời kết bạn.
     *
     * Endpoint:
     * DELETE /api/friends/requests/{requestId}
     *
     * @param authentication thông tin user hiện tại
     * @param requestId id của lời mời cần từ chối
     * @return message thành công hoặc lỗi
     */
    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<?> rejectRequest(Authentication authentication, @PathVariable String requestId) {
        try {
            // Lấy userId hiện tại
            String userId = (String) authentication.getPrincipal();

            // Gọi service từ chối lời mời
            MessageResponse response = friendService.rejectRequest(userId, requestId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * API xóa 1 người khỏi danh sách bạn bè.
     *
     * Endpoint:
     * DELETE /api/friends/{friendUserId}
     *
     * @param authentication thông tin user hiện tại
     * @param friendUserId id của người bạn cần xóa
     * @return message thành công hoặc lỗi
     */
    @DeleteMapping("/{friendUserId}")
    public ResponseEntity<?> removeFriend(Authentication authentication, @PathVariable String friendUserId) {
        try {
            // Lấy userId hiện tại
            String userId = (String) authentication.getPrincipal();

            // Gọi service xóa bạn
            MessageResponse response = friendService.removeFriend(userId, friendUserId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * API lấy lịch sử chat giữa user hiện tại và 1 người bạn.
     *
     * Endpoint:
     * GET /api/friends/chat/{friendUserId}
     *
     * @param authentication thông tin user hiện tại
     * @param friendUserId id người bạn cần xem lịch sử chat
     * @return danh sách tin nhắn hoặc message lỗi
     */
    @GetMapping("/chat/{friendUserId}")
    public ResponseEntity<?> getChatHistory(Authentication authentication, @PathVariable String friendUserId) {
        try {
            // Lấy userId hiện tại
            String userId = (String) authentication.getPrincipal();

            // Gọi service lấy lịch sử chat
            List<FriendChatMessageResponse> response = friendService.getChatHistory(userId, friendUserId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
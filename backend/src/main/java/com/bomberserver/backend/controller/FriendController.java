package com.bomberserver.backend.controller;

import com.bomberserver.backend.dto.common.MessageResponse;
import com.bomberserver.backend.dto.friend.*;
import com.bomberserver.backend.service.FriendService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(Authentication authentication, @RequestParam(defaultValue = "") String keyword) {
        try {
            String userId = (String) authentication.getPrincipal();
            List<FriendSearchItemResponse> response = friendService.searchUsers(userId, keyword);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getFriends(Authentication authentication) {
        try {
            String userId = (String) authentication.getPrincipal();
            List<FriendListItemResponse> response = friendService.getFriends(userId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/requests/incoming")
    public ResponseEntity<?> getIncomingRequests(Authentication authentication) {
        try {
            String userId = (String) authentication.getPrincipal();
            List<FriendRequestResponse> response = friendService.getIncomingRequests(userId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/requests/outgoing")
    public ResponseEntity<?> getOutgoingRequests(Authentication authentication) {
        try {
            String userId = (String) authentication.getPrincipal();
            List<FriendRequestResponse> response = friendService.getOutgoingRequests(userId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/requests")
    public ResponseEntity<?> sendRequest(Authentication authentication, @Valid @RequestBody CreateFriendRequest request) {
        try {
            String userId = (String) authentication.getPrincipal();
            MessageResponse response = friendService.sendFriendRequest(userId, request.targetUserId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<?> acceptRequest(Authentication authentication, @PathVariable String requestId) {
        try {
            String userId = (String) authentication.getPrincipal();
            MessageResponse response = friendService.acceptRequest(userId, requestId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<?> rejectRequest(Authentication authentication, @PathVariable String requestId) {
        try {
            String userId = (String) authentication.getPrincipal();
            MessageResponse response = friendService.rejectRequest(userId, requestId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @DeleteMapping("/{friendUserId}")
    public ResponseEntity<?> removeFriend(Authentication authentication, @PathVariable String friendUserId) {
        try {
            String userId = (String) authentication.getPrincipal();
            MessageResponse response = friendService.removeFriend(userId, friendUserId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/chat/{friendUserId}")
    public ResponseEntity<?> getChatHistory(Authentication authentication, @PathVariable String friendUserId) {
        try {
            String userId = (String) authentication.getPrincipal();
            List<FriendChatMessageResponse> response = friendService.getChatHistory(userId, friendUserId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
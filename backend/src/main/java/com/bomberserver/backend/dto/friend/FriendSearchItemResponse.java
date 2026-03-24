package com.bomberserver.backend.dto.friend;

public record FriendSearchItemResponse(
        String userId,
        String username,
        String characterName,
        String avatarCode,
        boolean online,
        String relationshipStatus,
        String requestId
) {
}
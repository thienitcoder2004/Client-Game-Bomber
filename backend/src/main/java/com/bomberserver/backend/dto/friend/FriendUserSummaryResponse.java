package com.bomberserver.backend.dto.friend;

public record FriendUserSummaryResponse(
        String userId,
        String username,
        String characterName,
        String avatarCode,
        boolean online
) {
}
package com.bomberserver.backend.dto.friend;

import java.time.Instant;

public record FriendChatMessageResponse(
        String id,
        String senderId,
        String receiverId,
        String content,
        Instant createdAt
) {
}
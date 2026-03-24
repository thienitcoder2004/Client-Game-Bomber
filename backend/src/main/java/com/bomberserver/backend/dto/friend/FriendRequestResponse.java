package com.bomberserver.backend.dto.friend;

import java.time.Instant;

public record FriendRequestResponse(
        String requestId,
        String direction,
        FriendUserSummaryResponse user,
        Instant createdAt
) {
}
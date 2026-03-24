package com.bomberserver.backend.dto.friend;

import java.time.Instant;

public record FriendListItemResponse(
        String friendshipId,
        FriendUserSummaryResponse user,
        Instant acceptedAt
) {
}
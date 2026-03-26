package com.bomberserver.backend.dto.friend;

import java.time.Instant;

/**
 * DTO đại diện cho 1 phần tử trong danh sách bạn bè.
 *
 * Khi frontend gọi API lấy danh sách bạn bè,
 * mỗi người bạn sẽ được trả về theo object này.
 */
public record FriendListItemResponse(

        /**
         * ID của quan hệ bạn bè / friendship document.
         */
        String friendshipId,

        /**
         * Thông tin tóm tắt của người bạn.
         */
        FriendUserSummaryResponse user,

        /**
         * Thời điểm 2 bên chính thức chấp nhận kết bạn.
         */
        Instant acceptedAt
) {
}
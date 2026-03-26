package com.bomberserver.backend.dto.friend;

import java.time.Instant;

/**
 * DTO trả về thông tin 1 lời mời kết bạn.
 *
 * Dùng cho:
 * - danh sách lời mời đến
 * - danh sách lời mời đã gửi
 */
public record FriendRequestResponse(

        /**
         * ID của request kết bạn.
         */
        String requestId,

        /**
         * Hướng của lời mời so với user hiện tại.
         *
         * Ví dụ:
         * - INCOMING : lời mời nhận được
         * - OUTGOING : lời mời đã gửi đi
         */
        String direction,

        /**
         * Thông tin tóm tắt của user liên quan đến lời mời.
         */
        FriendUserSummaryResponse user,

        /**
         * Thời điểm tạo lời mời kết bạn.
         */
        Instant createdAt
) {
}
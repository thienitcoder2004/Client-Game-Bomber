package com.bomberserver.backend.dto.friend;

/**
 * DTO trả về kết quả tìm kiếm user để kết bạn.
 *
 * Khi người dùng nhập từ khóa tìm bạn,
 * backend sẽ trả danh sách user dưới dạng object này.
 */
public record FriendSearchItemResponse(

        /**
         * ID của user được tìm thấy.
         */
        String userId,

        /**
         * Username của user đó.
         */
        String username,

        /**
         * Tên nhân vật trong game.
         */
        String characterName,

        /**
         * Mã avatar để frontend hiển thị đúng ảnh nhân vật.
         */
        String avatarCode,

        /**
         * Trạng thái online của user.
         *
         * true  = đang online
         * false = đang offline
         */
        boolean online,

        /**
         * Trạng thái quan hệ hiện tại giữa user đăng nhập và user tìm thấy.
         *
         * Ví dụ:
         * - NONE
         * - PENDING
         * - ACCEPTED
         * - SELF
         */
        String relationshipStatus,

        /**
         * ID của lời mời kết bạn nếu đang tồn tại.
         * Nếu chưa có request nào thì có thể null.
         */
        String requestId
) {
}
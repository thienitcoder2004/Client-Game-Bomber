package com.bomberserver.backend.dto.friend;

/**
 * DTO chứa thông tin tóm tắt của 1 user trong chức năng bạn bè.
 *
 * Dùng lồng bên trong các response khác như:
 * - danh sách bạn bè
 * - danh sách lời mời
 * - kết quả tìm kiếm
 */
public record FriendUserSummaryResponse(

        /**
         * ID của user.
         */
        String userId,

        /**
         * Username của user.
         */
        String username,

        /**
         * Tên nhân vật của user trong game.
         */
        String characterName,

        /**
         * Mã avatar để frontend hiển thị ảnh nhân vật tương ứng.
         */
        String avatarCode,

        /**
         * Trạng thái online của user.
         */
        boolean online
) {
}
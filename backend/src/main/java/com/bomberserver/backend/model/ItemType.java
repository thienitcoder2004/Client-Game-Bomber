package com.bomberserver.backend.model;

/**
 * Enum định nghĩa toàn bộ loại item có trong game.
 */
public enum ItemType {

    /**
     * Tăng số lượng bom tối đa có thể đặt cùng lúc.
     */
    BOMB_UP,

    /**
     * Tăng bán kính nổ của bom.
     */
    FLAME_UP,

    /**
     * Tăng tốc độ di chuyển của người chơi.
     */
    SPEED_UP,

    /**
     * Tạo khiên bảo vệ tạm thời.
     */
    SHIELD,

    /**
     * Tăng thêm mạng sống.
     */
    HEART,

    /**
     * Item dịch chuyển ngay khi dùng.
     */
    TELEPORT,

    /**
     * Item gắn hiệu ứng ngẫu nhiên cho quả bom kế tiếp.
     */
    RANDOM_BOMB,

    /**
     * Item gắn hiệu ứng đóng băng cho quả bom kế tiếp.
     */
    FREEZE_BOMB
}
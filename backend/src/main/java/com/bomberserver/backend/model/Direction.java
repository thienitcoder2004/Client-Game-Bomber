package com.bomberserver.backend.model;

/**
 * Enum biểu diễn hướng di chuyển / quay mặt của người chơi.
 *
 * Dùng trong:
 * - di chuyển nhân vật
 * - hiển thị sprite đúng hướng
 * - xác định hướng hành động trên client
 */
public enum Direction {

    /**
     * Hướng lên trên.
     */
    up,

    /**
     * Hướng xuống dưới.
     */
    down,

    /**
     * Hướng sang trái.
     */
    left,

    /**
     * Hướng sang phải.
     */
    right
}
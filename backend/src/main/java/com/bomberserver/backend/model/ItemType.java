package com.bomberserver.backend.model;

public enum ItemType {
    BOMB_UP,
    FLAME_UP,
    SPEED_UP,
    SHIELD,
    HEART,
    // item dịch chuyển ngay khi dùng
    TELEPORT,
    // item gắn hiệu ứng cho quả bom kế tiếp
    RANDOM_BOMB,
    FREEZE_BOMB
}
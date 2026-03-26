package com.bomberserver.backend.model;

/**
 * Model đại diện cho 1 item đang nằm trên map.
 *
 * Item có thể xuất hiện khi:
 * - phá block mềm
 * - phần thưởng ngẫu nhiên
 * - logic drop trong trận
 */
public class Item {

    /**
     * ID duy nhất của item.
     */
    public String id;

    /**
     * Hàng của item trên map.
     */
    public int row;

    /**
     * Cột của item trên map.
     */
    public int col;

    /**
     * Loại item.
     *
     * Xác định item này là tăng bom, tăng lửa, shield, heart...
     */
    public ItemType type;

    /**
     * Constructor rỗng.
     */
    public Item() {
    }

    /**
     * Constructor đầy đủ.
     *
     * @param id id item
     * @param row hàng item
     * @param col cột item
     * @param type loại item
     */
    public Item(String id, int row, int col, ItemType type) {
        this.id = id;
        this.row = row;
        this.col = col;
        this.type = type;
    }
}
package com.bomberserver.backend.model;

/**
 * Model đại diện cho 1 ô lửa trong vụ nổ.
 *
 * Một vụ nổ có thể gồm nhiều ô lửa:
 * - ô trung tâm
 * - ô lan lên
 * - ô lan xuống
 * - ô lan trái
 * - ô lan phải
 */
public class FlameCell {

    /**
     * Hàng của ô lửa.
     */
    public int row;

    /**
     * Cột của ô lửa.
     */
    public int col;

    /**
     * Loại ô lửa.
     *
     * Thường dùng để client biết nên vẽ sprite nào.
     * Ví dụ:
     * - center
     * - up
     * - down
     * - left
     * - right
     * - horizontal
     * - vertical
     */
    public String kind;

    /**
     * Constructor rỗng.
     */
    public FlameCell() {
    }

    /**
     * Constructor đầy đủ.
     *
     * @param row hàng ô lửa
     * @param col cột ô lửa
     * @param kind loại ô lửa
     */
    public FlameCell(int row, int col, String kind) {
        this.row = row;
        this.col = col;
        this.kind = kind;
    }
}
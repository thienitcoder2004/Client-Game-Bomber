package com.bomberserver.backend.model;

/**
 * Model đại diện cho 1 quả bom đang tồn tại trong trận đấu.
 *
 * Bom sẽ được tạo khi người chơi đặt bom xuống map.
 * Sau một khoảng thời gian nhất định, bom sẽ phát nổ.
 */
public class Bomb {

    /**
     * ID duy nhất của quả bom.
     *
     * Dùng để phân biệt giữa các bom với nhau,
     * tiện cho việc quản lý, xóa hoặc đồng bộ với client.
     */
    public String id;

    /**
     * ID người chơi đã đặt quả bom này.
     *
     * Dùng để:
     * - biết ai là chủ sở hữu bom
     * - tính điểm kill
     * - giảm / hoàn lại số bom đã đặt của người chơi
     */
    public int ownerId;

    /**
     * Hàng hiện tại của bom trên map.
     */
    public int row;

    /**
     * Cột hiện tại của bom trên map.
     */
    public int col;

    /**
     * Thời điểm bom được đặt xuống.
     *
     * Dùng để tính xem bom đã đến lúc nổ chưa,
     * ví dụ: nếu currentTime - placedAt >= bombFuseMs thì nổ.
     */
    public long placedAt;

    /**
     * Bán kính nổ của bom.
     *
     * Ví dụ:
     * - range = 1: nổ lan 1 ô mỗi hướng
     * - range = 2: nổ lan 2 ô mỗi hướng
     */
    public int range;

    /**
     * Cho biết bom này có nổ theo kiểu ngẫu nhiên hay không.
     *
     * true  = bom random
     * false = bom thường
     */
    public boolean randomPattern;

    /**
     * Cho biết bom này có hiệu ứng đóng băng người chơi hay không.
     *
     * true  = bom băng
     * false = bom thường
     */
    public boolean freezeEffect;

    /**
     * Constructor rỗng để dễ mapping dữ liệu.
     */
    public Bomb() {
    }

    /**
     * Constructor đầy đủ để tạo nhanh 1 quả bom mới.
     *
     * @param id id bom
     * @param ownerId id người chơi đặt bom
     * @param row hàng của bom
     * @param col cột của bom
     * @param placedAt thời điểm đặt bom
     * @param range bán kính nổ
     * @param randomPattern có phải bom random hay không
     * @param freezeEffect có phải bom băng hay không
     */
    public Bomb(
            String id,
            int ownerId,
            int row,
            int col,
            long placedAt,
            int range,
            boolean randomPattern,
            boolean freezeEffect
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.row = row;
        this.col = col;
        this.placedAt = placedAt;
        this.range = range;
        this.randomPattern = randomPattern;
        this.freezeEffect = freezeEffect;
    }
}
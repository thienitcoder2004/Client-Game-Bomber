package com.bomberserver.backend.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Model đại diện cho 1 vụ nổ sinh ra sau khi bom phát nổ.
 *
 * Vụ nổ sẽ tồn tại trong một khoảng thời gian ngắn,
 * sau đó biến mất khỏi game state.
 */
public class Explosion {

    /**
     * ID của vụ nổ.
     */
    public String id;

    /**
     * ID người chơi sở hữu vụ nổ này.
     *
     * Thường chính là người đã đặt quả bom gây ra vụ nổ.
     */
    public int ownerId;

    /**
     * Hàng trung tâm của vụ nổ.
     *
     * Đây là vị trí gốc nơi bom phát nổ.
     */
    public int row;

    /**
     * Cột trung tâm của vụ nổ.
     */
    public int col;

    /**
     * Thời điểm vụ nổ bắt đầu.
     */
    public long startedAt;

    /**
     * Thời gian tồn tại của vụ nổ tính bằng mili giây.
     *
     * Sau khoảng thời gian này, explosion sẽ bị xóa khỏi state.
     */
    public long duration;

    /**
     * Danh sách các ô lửa thuộc vụ nổ này.
     *
     * Mỗi FlameCell đại diện cho 1 ô lửa trên map.
     */
    public List<FlameCell> cells = new ArrayList<>();

    /**
     * Cho client biết vụ nổ này có phải đến từ bom random hay không.
     *
     * Dùng để frontend có thể vẽ hiệu ứng khác nếu muốn.
     */
    public boolean randomPattern;

    /**
     * Cho client biết vụ nổ này có hiệu ứng đóng băng hay không.
     *
     * Nếu true thì người chơi dính vụ nổ có thể bị freeze.
     */
    public boolean freezeEffect;

    /**
     * Constructor rỗng.
     */
    public Explosion() {
    }

    /**
     * Constructor đầy đủ để tạo nhanh 1 vụ nổ.
     *
     * @param id id vụ nổ
     * @param ownerId id người chơi sở hữu vụ nổ
     * @param row hàng trung tâm
     * @param col cột trung tâm
     * @param startedAt thời điểm bắt đầu nổ
     * @param duration thời gian tồn tại
     * @param cells danh sách ô lửa
     * @param randomPattern có phải nổ ngẫu nhiên hay không
     * @param freezeEffect có hiệu ứng đóng băng hay không
     */
    public Explosion(
            String id,
            int ownerId,
            int row,
            int col,
            long startedAt,
            long duration,
            List<FlameCell> cells,
            boolean randomPattern,
            boolean freezeEffect
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.row = row;
        this.col = col;
        this.startedAt = startedAt;
        this.duration = duration;
        this.cells = cells;
        this.randomPattern = randomPattern;
        this.freezeEffect = freezeEffect;
    }
}
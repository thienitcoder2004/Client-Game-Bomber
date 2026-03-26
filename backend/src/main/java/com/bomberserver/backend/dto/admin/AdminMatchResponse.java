package com.bomberserver.backend.dto.admin;

import java.time.Instant;
import java.util.List;

/**
 * DTO trả dữ liệu lịch sử trận đấu cho phía ADMIN.
 *
 * Dùng khi admin gọi API xem danh sách các trận đã diễn ra.
 * Đây là object trả về cho frontend, không phải document lưu trực tiếp trong DB.
 */
public class AdminMatchResponse {

    /**
     * ID của trận đấu trong database.
     */
    public String id;

    /**
     * Mã phòng của trận đấu.
     */
    public String roomCode;

    /**
     * ID của user chiến thắng.
     */
    public String winnerUserId;

    /**
     * Tên nhân vật của người chiến thắng.
     */
    public String winnerCharacterName;

    /**
     * Thời điểm bắt đầu trận đấu.
     */
    public Instant startedAt;

    /**
     * Thời điểm kết thúc trận đấu.
     */
    public Instant endedAt;

    /**
     * Số lượng người chơi tham gia trận.
     */
    public int playerCount;

    /**
     * Danh sách tên nhân vật của những người chơi tham gia trận.
     */
    public List<String> playerNames;

    /**
     * Constructor rỗng để framework hoặc code map dữ liệu dễ hơn.
     */
    public AdminMatchResponse() {
    }

    /**
     * Constructor đầy đủ để tạo nhanh response trả về cho frontend.
     *
     * @param id id trận đấu
     * @param roomCode mã phòng
     * @param winnerUserId id người thắng
     * @param winnerCharacterName tên nhân vật người thắng
     * @param startedAt thời gian bắt đầu
     * @param endedAt thời gian kết thúc
     * @param playerCount số người chơi
     * @param playerNames danh sách tên người chơi
     */
    public AdminMatchResponse(
            String id,
            String roomCode,
            String winnerUserId,
            String winnerCharacterName,
            Instant startedAt,
            Instant endedAt,
            int playerCount,
            List<String> playerNames
    ) {
        this.id = id;
        this.roomCode = roomCode;
        this.winnerUserId = winnerUserId;
        this.winnerCharacterName = winnerCharacterName;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.playerCount = playerCount;
        this.playerNames = playerNames;
    }
}
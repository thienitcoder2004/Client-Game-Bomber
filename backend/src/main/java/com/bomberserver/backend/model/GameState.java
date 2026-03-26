package com.bomberserver.backend.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Model đại diện cho toàn bộ trạng thái hiện tại của trận đấu.
 *
 * Đây là object rất quan trọng vì server sẽ gửi GameState về client
 * để đồng bộ:
 * - map
 * - người chơi
 * - bom
 * - vụ nổ
 * - item
 * - trạng thái thắng thua
 * - trạng thái phòng chờ
 */
public class GameState {

    /**
     * Ma trận map của game.
     *
     * Ví dụ:
     * - 0 = ô trống
     * - 1 = tường cứng
     * - 2 = tường mềm
     *
     * Tùy logic server bạn đang quy ước.
     */
    public int[][] board;

    /**
     * Danh sách tất cả người chơi trong trận.
     */
    public List<Player> players = new ArrayList<>();

    /**
     * Danh sách các quả bom đang tồn tại trên map.
     */
    public List<Bomb> bombs = new ArrayList<>();

    /**
     * Danh sách các vụ nổ đang còn hiệu lực.
     */
    public List<Explosion> explosions = new ArrayList<>();

    /**
     * Danh sách item đang xuất hiện trên map.
     */
    public List<Item> items = new ArrayList<>();

    /**
     * Trận đấu đã kết thúc hay chưa.
     *
     * true  = đã kết thúc
     * false = vẫn đang diễn ra
     */
    public boolean gameOver;

    /**
     * ID người chiến thắng.
     *
     * Có thể null nếu chưa có người thắng
     * hoặc trận chưa kết thúc.
     */
    public Integer winnerId;

    /**
     * Chuỗi mô tả kết quả trận đấu.
     *
     * Ví dụ:
     * - "Player 1 wins"
     * - "Team A thắng"
     */
    public String resultMessage;

    // ===== Trạng thái chờ phòng =====

    /**
     * Cho biết trận đang ở trạng thái chờ đủ người chơi hay không.
     *
     * true  = đang chờ người
     * false = không còn chờ nữa
     */
    public boolean waitingForPlayers;

    /**
     * Cho biết game đã bắt đầu hay chưa.
     *
     * true  = trận đã bắt đầu
     * false = vẫn đang ở phòng chờ
     */
    public boolean gameStarted;

    /**
     * Số người hiện đang kết nối trong phòng.
     */
    public int connectedPlayers;

    /**
     * Số người cần đủ để bắt đầu trận.
     */
    public int requiredPlayers;

    /**
     * Số giây còn lại của countdown trước khi bắt đầu.
     *
     * Có thể null nếu chưa vào giai đoạn đếm ngược.
     */
    public Integer countdownSeconds;

    // ===== kiểu trận =====
    // SOLO hoặc DUO

    /**
     * Chế độ trận đấu hiện tại.
     *
     * Ví dụ:
     * - SOLO
     * - DUO
     */
    public String matchMode;

    /**
     * Constructor rỗng.
     */
    public GameState() {
    }

    /**
     * Constructor đầy đủ để tạo nhanh 1 game state.
     *
     * @param board bản đồ
     * @param players danh sách người chơi
     * @param bombs danh sách bom
     * @param explosions danh sách vụ nổ
     * @param items danh sách item
     * @param gameOver trạng thái kết thúc trận
     * @param winnerId id người thắng
     * @param resultMessage nội dung kết quả
     * @param waitingForPlayers có đang chờ người không
     * @param gameStarted trận đã bắt đầu chưa
     * @param connectedPlayers số người hiện tại
     * @param requiredPlayers số người cần thiết
     * @param countdownSeconds thời gian đếm ngược còn lại
     * @param matchMode kiểu trận
     */
    public GameState(
            int[][] board,
            List<Player> players,
            List<Bomb> bombs,
            List<Explosion> explosions,
            List<Item> items,
            boolean gameOver,
            Integer winnerId,
            String resultMessage,
            boolean waitingForPlayers,
            boolean gameStarted,
            int connectedPlayers,
            int requiredPlayers,
            Integer countdownSeconds,
            String matchMode
    ) {
        this.board = board;
        this.players = players;
        this.bombs = bombs;
        this.explosions = explosions;
        this.items = items;
        this.gameOver = gameOver;
        this.winnerId = winnerId;
        this.resultMessage = resultMessage;
        this.waitingForPlayers = waitingForPlayers;
        this.gameStarted = gameStarted;
        this.connectedPlayers = connectedPlayers;
        this.requiredPlayers = requiredPlayers;
        this.countdownSeconds = countdownSeconds;
        this.matchMode = matchMode;
    }
}
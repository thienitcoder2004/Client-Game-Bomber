package com.bomberserver.backend.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Model đại diện cho 1 người chơi trong trận.
 *
 * Class này lưu:
 * - vị trí
 * - hướng
 * - số mạng
 * - chỉ số bom
 * - tốc độ
 * - hiệu ứng trạng thái
 * - thông tin hiển thị
 * - team
 * - bot
 * - inventory
 */
public class Player {

    /**
     * ID nội bộ của người chơi trong trận.
     *
     * Đây thường là id số để xử lý gameplay nhanh.
     */
    public int id;

    /**
     * Hàng hiện tại của người chơi trên map.
     */
    public int row;

    /**
     * Cột hiện tại của người chơi trên map.
     */
    public int col;

    /**
     * Hướng hiện tại người chơi đang quay mặt / di chuyển.
     */
    public Direction direction;

    /**
     * Số mạng còn lại của người chơi.
     */
    public int lives;

    /**
     * Mốc thời gian mà đến trước đó người chơi còn bất tử.
     *
     * Dùng để tránh bị ăn damage liên tục trong thời gian ngắn.
     */
    public long invulnerableUntil;

    /**
     * Số lượng bom tối đa mà người chơi được đặt cùng lúc.
     */
    public int maxBombs;

    /**
     * Bán kính nổ hiện tại của bom người chơi.
     */
    public int bombRange;

    /**
     * Cấp tốc độ hiện tại.
     */
    public int speedLevel;

    /**
     * Tốc độ gốc của người chơi.
     *
     * Dùng khi có hiệu ứng tăng tốc tạm thời,
     * sau đó hết hiệu lực thì trả lại baseSpeedLevel.
     */
    public int baseSpeedLevel;

    /**
     * Mốc thời gian kết thúc hiệu ứng tăng tốc.
     */
    public long speedBoostUntil;

    /**
     * Mốc thời gian kết thúc hiệu ứng đóng băng.
     *
     * Nếu currentTime < frozenUntil thì người chơi đang bị freeze.
     */
    public long frozenUntil;

    /**
     * Đánh dấu quả bom kế tiếp có hiệu ứng random hay không.
     */
    public boolean nextBombRandom;

    /**
     * Đánh dấu quả bom kế tiếp có hiệu ứng đóng băng hay không.
     */
    public boolean nextBombFreeze;

    /**
     * Tổng số bom người chơi đã đặt trong trận.
     */
    public int bombsPlaced;

    /**
     * Tổng số kill người chơi đạt được.
     */
    public int kills;

    /**
     * Tổng số lần người chơi bị chết.
     */
    public int deaths;

    /**
     * Điểm OVR tổng hợp để xếp hạng cuối trận.
     */
    public int ovr;

    /**
     * ID user thật trong hệ thống tài khoản.
     *
     * Có thể null với bot hoặc player chưa map user.
     */
    public String userId;

    /**
     * Tên hiển thị chung.
     */
    public String displayName;

    /**
     * Tên nhân vật trong game.
     */
    public String characterName;

    /**
     * Giới tính nhân vật.
     */
    public String gender;

    /**
     * Mã avatar của nhân vật.
     */
    public String avatarCode;

    // ===== TEAM =====
    // SOLO: mỗi người là 1 team riêng
    // DUO: 2 người có thể cùng 1 team

    /**
     * ID đội của người chơi.
     *
     * Ví dụ:
     * - SOLO: mỗi người có 1 team riêng
     * - DUO: 2 người có thể cùng teamId
     */
    public String teamId;

    // ===== BOT =====

    /**
     * Đánh dấu người chơi này có phải bot hay không.
     *
     * true  = bot
     * false = người chơi thật
     */
    public boolean bot;

    /**
     * Trạng thái sẵn sàng của người chơi / bot trong phòng.
     */
    public boolean ready;

    /**
     * Thời điểm tiếp theo bot sẽ suy nghĩ / ra quyết định.
     *
     * Dùng để giới hạn tần suất AI xử lý.
     */
    public long botNextThinkAt;

    /**
     * Thời điểm bot được phép đặt bom tiếp theo.
     *
     * Dùng để tránh bot spam bom quá nhanh.
     */
    public long botBombCooldownUntil;

    /**
     * Danh sách item đang có trong túi đồ của người chơi.
     */
    public List<ItemType> inventory = new ArrayList<>();

    /**
     * Constructor rỗng.
     */
    public Player() {
    }

    /**
     * Constructor cơ bản để tạo 1 người chơi mới trong trận.
     *
     * Các giá trị mặc định được set sẵn như:
     * - maxBombs = 1
     * - bombRange = 1
     * - speedLevel = 1
     * - displayName mặc định
     * - team riêng mặc định
     *
     * @param id id người chơi trong trận
     * @param row vị trí hàng ban đầu
     * @param col vị trí cột ban đầu
     * @param direction hướng ban đầu
     * @param lives số mạng ban đầu
     */
    public Player(int id, int row, int col, Direction direction, int lives) {
        this.id = id;
        this.row = row;
        this.col = col;
        this.direction = direction;
        this.lives = lives;

        // Chưa có bất tử
        this.invulnerableUntil = 0L;

        // Chỉ số gameplay mặc định
        this.maxBombs = 1;
        this.bombRange = 1;
        this.speedLevel = 1;
        this.baseSpeedLevel = 1;
        this.speedBoostUntil = 0L;

        // Chưa bị đóng băng, chưa có hiệu ứng bom đặc biệt
        this.frozenUntil = 0L;
        this.nextBombRandom = false;
        this.nextBombFreeze = false;

        // Chỉ số thống kê ban đầu
        this.bombsPlaced = 0;
        this.kills = 0;
        this.deaths = 0;
        this.ovr = 0;

        // Thông tin tài khoản / hiển thị mặc định
        this.userId = null;
        this.displayName = "Player-" + id;
        this.characterName = "Player " + id;
        this.gender = "";
        this.avatarCode = "";

        // Mặc định nếu chưa gán theo mode thì coi như mỗi người 1 team riêng
        this.teamId = "P" + id;

        // Mặc định là người chơi thường, chưa phải bot
        this.bot = false;
        this.ready = false;
        this.botNextThinkAt = 0L;
        this.botBombCooldownUntil = 0L;

        // Túi đồ ban đầu rỗng
        this.inventory = new ArrayList<>();
    }
}
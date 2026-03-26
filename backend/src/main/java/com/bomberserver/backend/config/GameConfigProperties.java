package com.bomberserver.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * Class dùng để đọc toàn bộ cấu hình gameplay từ file game-config.properties
 * với prefix là "game".
 *
 * Ví dụ file cấu hình:
 * game.board.rows=13
 * game.player.start-lives=3
 * game.match.default-required-players=4
 *
 * Mục đích:
 * - Gom tất cả thông số game về 1 nơi
 * - Dễ chỉnh gameplay mà không cần sửa logic code nhiều nơi
 */
@Component
@ConfigurationProperties(prefix = "game")
@PropertySource(value = "classpath:game-config.properties", encoding = "UTF-8")
public class GameConfigProperties {

    /**
     * Nhóm cấu hình bản đồ.
     */
    private final Board board = new Board();

    /**
     * Nhóm cấu hình mặc định cho người chơi.
     */
    private final Player player = new Player();

    /**
     * Nhóm cấu hình cho trận đấu / phòng chơi.
     */
    private final Match match = new Match();

    /**
     * Nhóm cấu hình thời gian trong game.
     */
    private final Timing timing = new Timing();

    /**
     * Nhóm cấu hình tỉ lệ rơi item.
     */
    private final Drop drop = new Drop();

    /**
     * Nhóm cấu hình vị trí spawn người chơi.
     */
    private final Spawn spawn = new Spawn();

    public Board getBoard() {
        return board;
    }

    public Player getPlayer() {
        return player;
    }

    public Match getMatch() {
        return match;
    }

    public Timing getTiming() {
        return timing;
    }

    public Drop getDrop() {
        return drop;
    }

    public Spawn getSpawn() {
        return spawn;
    }

    /**
     * Lấy cooldown di chuyển dựa theo cấp tốc độ hiện tại.
     *
     * speedLevel càng cao thì cooldown càng thấp,
     * nghĩa là nhân vật di chuyển càng nhanh.
     *
     * @param speedLevel cấp tốc độ hiện tại của người chơi
     * @return thời gian chờ giữa 2 lần di chuyển (ms)
     */
    public long getMoveCooldownForSpeedLevel(int speedLevel) {
        return switch (speedLevel) {
            case 2 -> timing.getMoveCooldownLevel2Ms();
            case 3 -> timing.getMoveCooldownLevel3Ms();
            case 4 -> timing.getMoveCooldownLevel4Ms();
            case 5 -> timing.getMoveCooldownLevel5Ms();
            default -> timing.getMoveCooldownLevel1Ms();
        };
    }

    /**
     * Lấy hàng spawn dựa theo player id.
     *
     * Ví dụ:
     * - player 1 spawn góc trên trái
     * - player 2 spawn góc trên phải
     * - player 3 spawn góc dưới trái
     * - player 4 spawn góc dưới phải
     *
     * @param playerId id người chơi
     * @return row spawn tương ứng
     */
    public int getSpawnRowForPlayer(int playerId) {
        return switch (playerId) {
            case 1 -> spawn.getP1Row();
            case 2 -> spawn.getP2Row();
            case 3 -> spawn.getP3Row();
            case 4 -> spawn.getP4Row();
            default -> spawn.getP1Row();
        };
    }

    /**
     * Lấy cột spawn dựa theo player id.
     *
     * @param playerId id người chơi
     * @return col spawn tương ứng
     */
    public int getSpawnColForPlayer(int playerId) {
        return switch (playerId) {
            case 1 -> spawn.getP1Col();
            case 2 -> spawn.getP2Col();
            case 3 -> spawn.getP3Col();
            case 4 -> spawn.getP4Col();
            default -> spawn.getP1Col();
        };
    }

    /**
     * Class con chứa cấu hình cho bản đồ.
     */
    public static class Board {

        /**
         * Số hàng của map.
         */
        private int rows = 13;

        /**
         * Số cột của map.
         */
        private int cols = 15;

        /**
         * Tỉ lệ sinh tường mềm.
         * Giá trị từ 0 đến 1.
         */
        private double softWallRate = 0.42;

        public int getRows() {
            return rows;
        }

        public void setRows(int rows) {
            this.rows = rows;
        }

        public int getCols() {
            return cols;
        }

        public void setCols(int cols) {
            this.cols = cols;
        }

        public double getSoftWallRate() {
            return softWallRate;
        }

        public void setSoftWallRate(double softWallRate) {
            this.softWallRate = softWallRate;
        }
    }

    /**
     * Class con chứa cấu hình mặc định cho người chơi.
     */
    public static class Player {

        /**
         * Số mạng ban đầu.
         */
        private int startLives = 3;

        /**
         * Số bom tối đa ban đầu có thể đặt cùng lúc.
         */
        private int startMaxBombs = 3;

        /**
         * Tầm nổ bom ban đầu.
         */
        private int startBombRange = 1;

        /**
         * Cấp tốc độ di chuyển ban đầu.
         */
        private int startSpeedLevel = 1;

        /**
         * Kích thước tối đa túi đồ / inventory.
         */
        private int maxInventorySize = 5;

        /**
         * Số bom tối đa có thể nâng cấp tới.
         */
        private int maxBombs = 5;

        /**
         * Tầm nổ tối đa có thể nâng cấp tới.
         */
        private int maxBombRange = 5;

        /**
         * Cấp tốc độ tối đa có thể nâng cấp tới.
         */
        private int maxSpeedLevel = 5;

        /**
         * Số mạng tối đa có thể đạt được.
         */
        private int maxLives = 5;

        /**
         * Thời gian tồn tại khiên bảo vệ (ms).
         */
        private long shieldDurationMs = 5000;

        public int getStartLives() {
            return startLives;
        }

        public void setStartLives(int startLives) {
            this.startLives = startLives;
        }

        public int getStartMaxBombs() {
            return startMaxBombs;
        }

        public void setStartMaxBombs(int startMaxBombs) {
            this.startMaxBombs = startMaxBombs;
        }

        public int getStartBombRange() {
            return startBombRange;
        }

        public void setStartBombRange(int startBombRange) {
            this.startBombRange = startBombRange;
        }

        public int getStartSpeedLevel() {
            return startSpeedLevel;
        }

        public void setStartSpeedLevel(int startSpeedLevel) {
            this.startSpeedLevel = startSpeedLevel;
        }

        public int getMaxInventorySize() {
            return maxInventorySize;
        }

        public void setMaxInventorySize(int maxInventorySize) {
            this.maxInventorySize = maxInventorySize;
        }

        public int getMaxBombs() {
            return maxBombs;
        }

        public void setMaxBombs(int maxBombs) {
            this.maxBombs = maxBombs;
        }

        public int getMaxBombRange() {
            return maxBombRange;
        }

        public void setMaxBombRange(int maxBombRange) {
            this.maxBombRange = maxBombRange;
        }

        public int getMaxSpeedLevel() {
            return maxSpeedLevel;
        }

        public void setMaxSpeedLevel(int maxSpeedLevel) {
            this.maxSpeedLevel = maxSpeedLevel;
        }

        public int getMaxLives() {
            return maxLives;
        }

        public void setMaxLives(int maxLives) {
            this.maxLives = maxLives;
        }

        public long getShieldDurationMs() {
            return shieldDurationMs;
        }

        public void setShieldDurationMs(long shieldDurationMs) {
            this.shieldDurationMs = shieldDurationMs;
        }
    }

    /**
     * Class con chứa cấu hình cho trận đấu.
     */
    public static class Match {

        /**
         * Số người mặc định cần để bắt đầu trận.
         */
        private int defaultRequiredPlayers = 4;

        /**
         * Số người tối thiểu cho phép.
         */
        private int minRequiredPlayers = 2;

        /**
         * Số người tối đa cho phép.
         */
        private int maxRequiredPlayers = 4;

        /**
         * Số giây đếm ngược trước khi bắt đầu.
         */
        private int startCountdownSeconds = 3;

        /**
         * Chu kỳ cập nhật game loop của server (ms).
         */
        private long tickRateMs = 100;

        public int getDefaultRequiredPlayers() {
            return defaultRequiredPlayers;
        }

        public void setDefaultRequiredPlayers(int defaultRequiredPlayers) {
            this.defaultRequiredPlayers = defaultRequiredPlayers;
        }

        public int getMinRequiredPlayers() {
            return minRequiredPlayers;
        }

        public void setMinRequiredPlayers(int minRequiredPlayers) {
            this.minRequiredPlayers = minRequiredPlayers;
        }

        public int getMaxRequiredPlayers() {
            return maxRequiredPlayers;
        }

        public void setMaxRequiredPlayers(int maxRequiredPlayers) {
            this.maxRequiredPlayers = maxRequiredPlayers;
        }

        public int getStartCountdownSeconds() {
            return startCountdownSeconds;
        }

        public void setStartCountdownSeconds(int startCountdownSeconds) {
            this.startCountdownSeconds = startCountdownSeconds;
        }

        public long getTickRateMs() {
            return tickRateMs;
        }

        public void setTickRateMs(long tickRateMs) {
            this.tickRateMs = tickRateMs;
        }
    }

    /**
     * Class con chứa toàn bộ thông số về thời gian trong game.
     */
    public static class Timing {

        /**
         * Thời gian bom phát nổ sau khi đặt (ms).
         */
        private long bombFuseMs = 1000;

        /**
         * Thời gian hiệu ứng nổ tồn tại (ms).
         */
        private long explosionMs = 350;

        /**
         * Thời gian bất tử sau khi bị trúng đòn / respawn / hit (ms).
         */
        private long invulnerableMs = 1400;

        /**
         * Thời gian đóng băng nếu trúng hiệu ứng freeze (ms).
         */
        private long freezeDurationMs = 3000;

        /**
         * Cooldown di chuyển tương ứng từng cấp tốc độ.
         * Số càng nhỏ thì đi càng nhanh.
         */
        private long moveCooldownLevel1Ms = 160;
        private long moveCooldownLevel2Ms = 135;
        private long moveCooldownLevel3Ms = 110;
        private long moveCooldownLevel4Ms = 90;
        private long moveCooldownLevel5Ms = 75;

        public long getBombFuseMs() {
            return bombFuseMs;
        }

        public void setBombFuseMs(long bombFuseMs) {
            this.bombFuseMs = bombFuseMs;
        }

        public long getExplosionMs() {
            return explosionMs;
        }

        public void setExplosionMs(long explosionMs) {
            this.explosionMs = explosionMs;
        }

        public long getInvulnerableMs() {
            return invulnerableMs;
        }

        public void setInvulnerableMs(long invulnerableMs) {
            this.invulnerableMs = invulnerableMs;
        }

        public long getFreezeDurationMs() {
            return freezeDurationMs;
        }

        public void setFreezeDurationMs(long freezeDurationMs) {
            this.freezeDurationMs = freezeDurationMs;
        }

        public long getMoveCooldownLevel1Ms() {
            return moveCooldownLevel1Ms;
        }

        public void setMoveCooldownLevel1Ms(long moveCooldownLevel1Ms) {
            this.moveCooldownLevel1Ms = moveCooldownLevel1Ms;
        }

        public long getMoveCooldownLevel2Ms() {
            return moveCooldownLevel2Ms;
        }

        public void setMoveCooldownLevel2Ms(long moveCooldownLevel2Ms) {
            this.moveCooldownLevel2Ms = moveCooldownLevel2Ms;
        }

        public long getMoveCooldownLevel3Ms() {
            return moveCooldownLevel3Ms;
        }

        public void setMoveCooldownLevel3Ms(long moveCooldownLevel3Ms) {
            this.moveCooldownLevel3Ms = moveCooldownLevel3Ms;
        }

        public long getMoveCooldownLevel4Ms() {
            return moveCooldownLevel4Ms;
        }

        public void setMoveCooldownLevel4Ms(long moveCooldownLevel4Ms) {
            this.moveCooldownLevel4Ms = moveCooldownLevel4Ms;
        }

        public long getMoveCooldownLevel5Ms() {
            return moveCooldownLevel5Ms;
        }

        public void setMoveCooldownLevel5Ms(long moveCooldownLevel5Ms) {
            this.moveCooldownLevel5Ms = moveCooldownLevel5Ms;
        }
    }

    /**
     * Class con chứa cấu hình tỉ lệ rơi item.
     */
    public static class Drop {

        /**
         * Tỉ lệ rơi item khi phá block.
         * 0.50 nghĩa là 50%.
         */
        private double itemRate = 0.50;

        public double getItemRate() {
            return itemRate;
        }

        public void setItemRate(double itemRate) {
            this.itemRate = itemRate;
        }
    }

    /**
     * Class con chứa vị trí spawn cho từng người chơi.
     */
    public static class Spawn {

        /**
         * Tọa độ spawn của player 1.
         */
        private int p1Row = 1;
        private int p1Col = 1;

        /**
         * Tọa độ spawn của player 2.
         */
        private int p2Row = 1;
        private int p2Col = 13;

        /**
         * Tọa độ spawn của player 3.
         */
        private int p3Row = 11;
        private int p3Col = 1;

        /**
         * Tọa độ spawn của player 4.
         */
        private int p4Row = 11;
        private int p4Col = 13;

        public int getP1Row() {
            return p1Row;
        }

        public void setP1Row(int p1Row) {
            this.p1Row = p1Row;
        }

        public int getP1Col() {
            return p1Col;
        }

        public void setP1Col(int p1Col) {
            this.p1Col = p1Col;
        }

        public int getP2Row() {
            return p2Row;
        }

        public void setP2Row(int p2Row) {
            this.p2Row = p2Row;
        }

        public int getP2Col() {
            return p2Col;
        }

        public void setP2Col(int p2Col) {
            this.p2Col = p2Col;
        }

        public int getP3Row() {
            return p3Row;
        }

        public void setP3Row(int p3Row) {
            this.p3Row = p3Row;
        }

        public int getP3Col() {
            return p3Col;
        }

        public void setP3Col(int p3Col) {
            this.p3Col = p3Col;
        }

        public int getP4Row() {
            return p4Row;
        }

        public void setP4Row(int p4Row) {
            this.p4Row = p4Row;
        }

        public int getP4Col() {
            return p4Col;
        }

        public void setP4Col(int p4Col) {
            this.p4Col = p4Col;
        }
    }
}
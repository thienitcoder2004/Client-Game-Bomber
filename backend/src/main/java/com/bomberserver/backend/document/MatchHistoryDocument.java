package com.bomberserver.backend.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Document lưu lịch sử trận đấu sau khi trận game kết thúc.
 *
 * Collection trong MongoDB:
 * match_histories
 *
 * Mỗi document tương ứng với 1 trận đấu.
 */
@Document("match_histories")
public class MatchHistoryDocument {

    /**
     * ID chính của lịch sử trận đấu.
     */
    @Id
    private String id;

    /**
     * Mã phòng của trận đấu.
     */
    private String roomCode;

    /**
     * ID của người chiến thắng.
     */
    private String winnerUserId;

    /**
     * Tên nhân vật của người chiến thắng.
     */
    private String winnerCharacterName;

    /**
     * Danh sách toàn bộ user đã tham gia trận đấu.
     *
     * Mục đích:
     * - hỗ trợ query nhanh lịch sử theo user
     * - ví dụ tìm tất cả trận mà 1 user đã chơi
     */
    private List<String> participantUserIds = new ArrayList<>();

    /**
     * Thời điểm trận đấu bắt đầu.
     */
    private Instant startedAt;

    /**
     * Thời điểm trận đấu kết thúc.
     */
    private Instant endedAt;

    /**
     * Danh sách kết quả chi tiết của từng người chơi trong trận.
     */
    private List<PlayerMatchResult> players = new ArrayList<>();

    /**
     * Class con lưu kết quả của từng người chơi trong 1 trận.
     */
    public static class PlayerMatchResult {

        /**
         * ID người chơi.
         */
        private String userId;

        /**
         * Tên nhân vật hiển thị của người chơi.
         */
        private String characterName;

        /**
         * Số bom đã đặt trong trận.
         */
        private int bombsPlaced;

        /**
         * Số mạng hạ gục được.
         */
        private int kills;

        /**
         * Số lần bị chết.
         */
        private int deaths;

        /**
         * Số mạng còn lại khi kết thúc trận.
         */
        private int livesLeft;

        /**
         * Điểm OVR tổng kết của người chơi.
         * Đây là điểm tổng hợp để xếp hạng sau trận.
         */
        private int ovr;

        /**
         * Đánh dấu người chơi này có phải người thắng không.
         */
        private boolean winner;

        public PlayerMatchResult() {
        }

        public String getUserId() {
            return userId;
        }

        public String getCharacterName() {
            return characterName;
        }

        public int getBombsPlaced() {
            return bombsPlaced;
        }

        public int getKills() {
            return kills;
        }

        public int getDeaths() {
            return deaths;
        }

        public int getLivesLeft() {
            return livesLeft;
        }

        public int getOvr() {
            return ovr;
        }

        public boolean isWinner() {
            return winner;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public void setCharacterName(String characterName) {
            this.characterName = characterName;
        }

        public void setBombsPlaced(int bombsPlaced) {
            this.bombsPlaced = bombsPlaced;
        }

        public void setKills(int kills) {
            this.kills = kills;
        }

        public void setDeaths(int deaths) {
            this.deaths = deaths;
        }

        public void setLivesLeft(int livesLeft) {
            this.livesLeft = livesLeft;
        }

        public void setOvr(int ovr) {
            this.ovr = ovr;
        }

        public void setWinner(boolean winner) {
            this.winner = winner;
        }
    }

    public String getId() {
        return id;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public String getWinnerUserId() {
        return winnerUserId;
    }

    public String getWinnerCharacterName() {
        return winnerCharacterName;
    }

    public List<String> getParticipantUserIds() {
        return participantUserIds;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public List<PlayerMatchResult> getPlayers() {
        return players;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public void setWinnerUserId(String winnerUserId) {
        this.winnerUserId = winnerUserId;
    }

    public void setWinnerCharacterName(String winnerCharacterName) {
        this.winnerCharacterName = winnerCharacterName;
    }

    public void setParticipantUserIds(List<String> participantUserIds) {
        this.participantUserIds = participantUserIds;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public void setPlayers(List<PlayerMatchResult> players) {
        this.players = players;
    }
}
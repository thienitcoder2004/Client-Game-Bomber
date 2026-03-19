package com.bomberserver.backend.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Lịch sử trận đấu lưu vào DB sau khi trận kết thúc
@Document("match_histories")
public class MatchHistoryDocument {

    @Id
    private String id;

    private String roomCode;

    private String winnerUserId;
    private String winnerCharacterName;

    // Để query lịch sử của 1 user nhanh hơn
    private List<String> participantUserIds = new ArrayList<>();

    private Instant startedAt;
    private Instant endedAt;

    private List<PlayerMatchResult> players = new ArrayList<>();

    public static class PlayerMatchResult {
        private String userId;
        private String characterName;
        private int bombsPlaced;
        private int kills;
        private int deaths;
        private int livesLeft;
        private int ovr;
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
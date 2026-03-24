package com.bomberserver.backend.dto.admin;

import java.time.Instant;
import java.util.List;

public class AdminMatchResponse {
    public String id;
    public String roomCode;
    public String winnerUserId;
    public String winnerCharacterName;
    public Instant startedAt;
    public Instant endedAt;
    public int playerCount;
    public List<String> playerNames;

    public AdminMatchResponse() {
    }

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
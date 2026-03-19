package com.bomberserver.backend.model;

import java.util.ArrayList;
import java.util.List;

public class GameState {
    public int[][] board;
    public List<Player> players = new ArrayList<>();
    public List<Bomb> bombs = new ArrayList<>();
    public List<Explosion> explosions = new ArrayList<>();
    public List<Item> items = new ArrayList<>();

    public boolean gameOver;
    public Integer winnerId;
    public String resultMessage;

    // ===== Trạng thái chờ phòng =====
    public boolean waitingForPlayers;
    public boolean gameStarted;
    public int connectedPlayers;
    public int requiredPlayers;
    public Integer countdownSeconds;

    public GameState() {
    }

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
            Integer countdownSeconds
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
    }
}
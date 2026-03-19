package com.bomberserver.backend.model;

import java.util.ArrayList;
import java.util.List;

public class Player {
    public int id;
    public int row;
    public int col;
    public Direction direction;
    public int lives;
    public long invulnerableUntil;

    public int maxBombs;
    public int bombRange;
    public int speedLevel;

    public int bombsPlaced;
    public int kills;
    public int deaths;
    public int ovr;

    public String userId;
    public String characterName;
    public String gender;
    public String avatarCode;

    public List<ItemType> inventory = new ArrayList<>();

    public Player() {
    }

    public Player(int id, int row, int col, Direction direction, int lives) {
        this.id = id;
        this.row = row;
        this.col = col;
        this.direction = direction;
        this.lives = lives;
        this.invulnerableUntil = 0L;

        this.maxBombs = 1;
        this.bombRange = 1;
        this.speedLevel = 1;

        this.bombsPlaced = 0;
        this.kills = 0;
        this.deaths = 0;
        this.ovr = 0;

        this.userId = "";
        this.characterName = "Player " + id;
        this.gender = "";
        this.avatarCode = "";

        this.inventory = new ArrayList<>();
    }
}
package com.bomberserver.backend.model;

public class Bomb {
    public String id;
    public int ownerId;
    public int row;
    public int col;
    public long placedAt;
    public int range;

    public Bomb() {
    }

    public Bomb(String id, int ownerId, int row, int col, long placedAt, int range) {
        this.id = id;
        this.ownerId = ownerId;
        this.row = row;
        this.col = col;
        this.placedAt = placedAt;
        this.range = range;
    }
}
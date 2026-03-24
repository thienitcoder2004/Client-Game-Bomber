package com.bomberserver.backend.model;

public class Bomb {
    public String id;
    public int ownerId;
    public int row;
    public int col;
    public long placedAt;
    public int range;

    // bom có nổ random hay không
    public boolean randomPattern;

    // bom có đóng băng người chơi hay không
    public boolean freezeEffect;

    public Bomb() {
    }

    public Bomb(
            String id,
            int ownerId,
            int row,
            int col,
            long placedAt,
            int range,
            boolean randomPattern,
            boolean freezeEffect
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.row = row;
        this.col = col;
        this.placedAt = placedAt;
        this.range = range;
        this.randomPattern = randomPattern;
        this.freezeEffect = freezeEffect;
    }
}
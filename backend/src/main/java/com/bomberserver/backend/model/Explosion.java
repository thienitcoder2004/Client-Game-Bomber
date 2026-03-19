package com.bomberserver.backend.model;

import java.util.ArrayList;
import java.util.List;

public class Explosion {
    public String id;
    public int ownerId;
    public int row;
    public int col;
    public long startedAt;
    public long duration;
    public List<FlameCell> cells = new ArrayList<>();

    public Explosion() {
    }

    public Explosion(String id, int ownerId, int row, int col, long startedAt, long duration, List<FlameCell> cells) {
        this.id = id;
        this.ownerId = ownerId;
        this.row = row;
        this.col = col;
        this.startedAt = startedAt;
        this.duration = duration;
        this.cells = cells;
    }
}
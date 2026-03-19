package com.bomberserver.backend.model;

public class FlameCell {
    public int row;
    public int col;
    public String kind;

    public FlameCell() {
    }

    public FlameCell(int row, int col, String kind) {
        this.row = row;
        this.col = col;
        this.kind = kind;
    }
}
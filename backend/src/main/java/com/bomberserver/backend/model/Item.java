package com.bomberserver.backend.model;

public class Item {
    public String id;
    public int row;
    public int col;
    public ItemType type;

    public Item() {
    }

    public Item(String id, int row, int col, ItemType type) {
        this.id = id;
        this.row = row;
        this.col = col;
        this.type = type;
    }
}
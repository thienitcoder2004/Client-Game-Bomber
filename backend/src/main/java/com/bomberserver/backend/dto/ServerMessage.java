package com.example.bomberserver.dto;

public class ServerMessage {
    public String type;
    public Object data;

    public ServerMessage() {
    }

    public ServerMessage(String type, Object data) {
        this.type = type;
        this.data = data;
    }
}
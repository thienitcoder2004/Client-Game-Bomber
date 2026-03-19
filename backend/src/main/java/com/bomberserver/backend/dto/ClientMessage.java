package com.bomberserver.backend.dto;

public class ClientMessage {
    public String type;
    public String direction;
    public Integer slotIndex; // slot vat pham

    public String roomCode;
    public String roomName;
    public Integer maxPlayers;
    public Boolean isPrivate;
}
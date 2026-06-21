package com.cardgame.exception;

public class RoomNotFoundException extends GameException {
    public RoomNotFoundException(String roomCode) {
        super("Room not found: " + roomCode);
    }
}


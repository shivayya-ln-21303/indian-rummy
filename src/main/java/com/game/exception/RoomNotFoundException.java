package com.game.exception;

/** Thrown when a requested room does not exist. */
public class RoomNotFoundException extends GameException {
    public RoomNotFoundException(String roomId) {
        super("Room not found: " + roomId);
    }
}


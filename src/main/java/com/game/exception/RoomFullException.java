package com.game.exception;

/** Thrown when a player tries to join a room that already has the maximum number of players. */
public class RoomFullException extends GameException {
    public RoomFullException(String roomId) {
        super("Room is full: " + roomId);
    }
}


package com.game.exception;

/** Thrown when trying to start a game that has already started or finished. */
public class GameAlreadyStartedException extends GameException {
    public GameAlreadyStartedException(String roomId) {
        super("Game has already started in room: " + roomId);
    }
}


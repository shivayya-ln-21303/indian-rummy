package com.game.exception;

/** Thrown when a player attempts a move that violates the game rules. */
public class InvalidMoveException extends GameException {
    public InvalidMoveException(String message) {
        super(message);
    }
}


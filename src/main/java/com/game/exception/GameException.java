package com.game.exception;

/**
 * Base exception for all game-domain errors.
 * Using a RuntimeException hierarchy keeps service signatures clean
 * while allowing Spring's {@code @ExceptionHandler} to intercept them.
 */
public class GameException extends RuntimeException {

    public GameException(String message) {
        super(message);
    }

    public GameException(String message, Throwable cause) {
        super(message, cause);
    }
}


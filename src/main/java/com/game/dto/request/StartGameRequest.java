package com.game.dto.request;

/**
 * Payload for START_GAME message (no body required).
 * Only the room owner (first player who created the room) may start the game.
 */
public record StartGameRequest() {}


package com.game.dto.request;

/**
 * Payload for PLAY_CARD message.
 * <pre>{ "cardIndex": 2 }</pre>
 * {@code cardIndex} is the zero-based index into the player's current hand.
 */
public record PlayCardRequest(int cardIndex) {}


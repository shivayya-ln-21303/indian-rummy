package com.cardgame.dto.request;

/**
 * DISCARD_CARD payload.
 * <pre>{ "cardId": "H-A-1" }</pre>
 */
public record DiscardCardRequest(String cardId) {}


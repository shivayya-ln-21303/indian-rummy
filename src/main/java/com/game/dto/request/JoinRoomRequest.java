package com.game.dto.request;

/**
 * Payload for JOIN_ROOM message.
 * <pre>{ "roomId": "ABC123", "playerName": "Bob" }</pre>
 * If {@code playerId} is present the server treats this as a <em>reconnect</em> attempt.
 */
public record JoinRoomRequest(String roomId, String playerName, String playerId) {}


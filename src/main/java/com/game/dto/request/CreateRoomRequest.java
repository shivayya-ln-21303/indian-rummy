package com.game.dto.request;

/**
 * Payload for CREATE_ROOM message.
 * <pre>{ "playerName": "Alice" }</pre>
 */
public record CreateRoomRequest(String playerName) {}


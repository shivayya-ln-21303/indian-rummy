package com.cardgame.dto.request;

/**
 * JOIN_ROOM payload.
 * When {@code playerId} is non-null this is treated as a reconnect.
 */
public record JoinRoomRequest(String roomCode, String playerName, String playerId) {}


package com.game.dto.response;

/**
 * Public view of a player — never exposes hand contents to other players.
 */
public record PlayerInfo(
        String playerId,
        String playerName,
        int cardCount,
        boolean connected,
        boolean isCurrentTurn
) {}


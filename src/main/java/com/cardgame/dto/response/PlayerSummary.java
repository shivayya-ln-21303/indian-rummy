package com.cardgame.dto.response;

/** Public view of a player — never exposes other players' card contents. */
public record PlayerSummary(
        String  playerId,
        String  playerName,
        int     seatIndex,
        int     cardCount,
        boolean connected,
        boolean isCurrentTurn,
        boolean jokerUnlocked
) {}


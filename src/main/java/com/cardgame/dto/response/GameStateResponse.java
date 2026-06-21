package com.cardgame.dto.response;

import com.cardgame.model.Card;
import com.cardgame.model.RoomStatus;

import java.util.List;

/**
 * Full game-state snapshot sent to ONE specific player.
 * {@code myCards} is personalised; other fields are public.
 */
public record GameStateResponse(
        String          roomId,
        String          roomCode,
        RoomStatus      status,
        List<PlayerSummary> players,
        List<Card>      myCards,
        List<List<Card>> myGroups,
        Card            topDiscard,
        int             deckSize,
        String          currentPlayerId,
        String          currentPlayerName,
        boolean         jokerUnlocked,
        int             turnTimeLeft,
        String          winnerId,
        String          winnerName,
        String          creatorId,
        java.util.Map<String, List<Card>> playerDiscards
) {}


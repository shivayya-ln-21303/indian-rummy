package com.game.dto.response;

import com.game.model.Card;
import com.game.model.RoomState;

import java.util.List;

/**
 * Snapshot of the game state sent to a specific player.
 * The {@code yourHand} field is personalised — each player only sees their own cards.
 */
public record GameStateResponse(
        String roomId,
        RoomState roomState,
        List<PlayerInfo> players,
        List<Card> yourHand,
        Card topDiscard,
        String currentPlayerId,
        String currentPlayerName,
        int deckSize,
        String winnerId
) {}


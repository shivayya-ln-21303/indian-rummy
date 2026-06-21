package com.cardgame.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single player in a game room.
 *
 * <h3>Hand size invariant</h3>
 * <ul>
 *   <li>Before drawing: 13 cards</li>
 *   <li>After drawing (before discard): 14 cards</li>
 *   <li>After discarding: 13 cards</li>
 * </ul>
 *
 * {@code sessionId} and {@code connected} are {@code volatile} so the
 * cleanup thread can read them safely without the room lock.
 */
@Getter
@Setter
public class Player {

    /** Stable across reconnects. */
    private final String playerId;
    private String playerName;

    /** Current WebSocket session — updated on reconnect. */
    private volatile String sessionId;

    /** Cards currently in hand. */
    private final List<Card> handCards = new ArrayList<>(14);

    /**
     * Player's current grouping of their hand cards.
     * Each inner list is one group (3 or 4 cards).
     * This is the player's proposed arrangement, not yet validated.
     * Updated via REARRANGE_CARDS; validated only on DECLARE_WIN.
     */
    private final List<List<Card>> groups = new ArrayList<>();

    private volatile boolean connected;
    private volatile LocalDateTime lastSeen;

    /** Index (0-based) of this player in the room's player list. */
    private int seatIndex;

    public Player(String playerId, String playerName, String sessionId, int seatIndex) {
        this.playerId   = playerId;
        this.playerName = playerName;
        this.sessionId  = sessionId;
        this.seatIndex  = seatIndex;
        this.connected  = true;
        this.lastSeen   = LocalDateTime.now();
    }

    public int handSize() { return handCards.size(); }

    /** Finds and returns the card with the given cardId, or null. */
    public Card findCard(String cardId) {
        return handCards.stream()
                .filter(c -> c.cardId().equals(cardId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        return "Player{" + playerName + ", hand=" + handSize() + "}";
    }
}


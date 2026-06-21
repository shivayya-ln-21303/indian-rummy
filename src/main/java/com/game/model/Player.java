package com.game.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a single player in a game room.
 * <p>
 * {@code handCards} is guarded by the owning {@link GameRoom}'s {@link ReentrantLock}.
 * {@code connected} and {@code sessionId} are marked {@code volatile} so that the
 * cleanup thread can read them without holding the room lock.
 * </p>
 */
@Getter
@Setter
public class Player {

    /** Stable identifier — survives reconnects. */
    private final String playerId;

    private String playerName;

    /** Current WebSocket session identifier — changes on reconnect. */
    private volatile String sessionId;

    /** Cards currently held in the player's hand. */
    private final List<Card> handCards = new ArrayList<>();

    /** Whether the WebSocket session is currently live. */
    private volatile boolean connected;

    /** Last time activity was recorded (used for cleanup). */
    private volatile LocalDateTime lastSeen;

    public Player(String playerId, String playerName, String sessionId) {
        this.playerId   = playerId;
        this.playerName = playerName;
        this.sessionId  = sessionId;
        this.connected  = true;
        this.lastSeen   = LocalDateTime.now();
    }

    /** Convenience: number of cards in hand. */
    public int handSize() {
        return handCards.size();
    }

    @Override
    public String toString() {
        return "Player{id=" + playerId + ", name=" + playerName + ", hand=" + handCards.size() + "}";
    }
}


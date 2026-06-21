package com.game.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a single game room and all its mutable state.
 *
 * <h3>Thread-safety contract</h3>
 * Every mutating operation on this room (player list, deck, discard pile,
 * turn index, state) <strong>must</strong> be performed while holding
 * {@link #getLock()}.  Read operations that need a consistent snapshot
 * also require the lock; cheap volatile reads (e.g. {@code roomState},
 * {@code lastActivityTime}) may be done lock-free.
 *
 * <p>The player list uses {@link CopyOnWriteArrayList} so that it can be
 * safely <em>iterated</em> (e.g., for broadcasting) without the lock,
 * while structural changes (add/remove) still happen inside the lock to
 * preserve invariants.</p>
 */
@Getter
@Setter
public class GameRoom {

    public static final int MAX_PLAYERS = 4;

    /** Unique identifier for this room. */
    private final String roomId;

    /** Current life-cycle state — volatile for cheap lock-free reads. */
    private volatile RoomState roomState;

    /**
     * Players in join order.  CopyOnWriteArrayList allows safe iteration
     * from broadcast threads without holding the lock.
     */
    private final List<Player> players = new CopyOnWriteArrayList<>();

    /** The draw pile. */
    private final Deck deck;

    /**
     * The discard pile.  Index 0 is the bottom; the last element is the top
     * (most recently played) card.
     */
    private final LinkedList<Card> discardPile = new LinkedList<>();

    /**
     * Index into {@code players} identifying whose turn it is.
     * Advances modulo {@code players.size()} after each action.
     */
    private volatile int currentTurnIndex;

    /** Player ID of the winner, set when the room transitions to FINISHED. */
    private volatile String winnerId;

    /** Wall-clock time at which this room was created. */
    private final LocalDateTime createdTime;

    /** Updated on every player action; used by the cleanup scheduler. */
    private volatile LocalDateTime lastActivityTime;

    /**
     * Per-room fair lock.
     * Fair mode prevents starvation under high contention while keeping
     * throughput acceptable for typical game workloads.
     */
    private final ReentrantLock lock = new ReentrantLock(true);

    // ---------------------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------------------

    public GameRoom(String roomId) {
        this.roomId           = roomId;
        this.roomState        = RoomState.WAITING;
        this.deck             = new Deck();
        this.createdTime      = LocalDateTime.now();
        this.lastActivityTime = LocalDateTime.now();
        this.currentTurnIndex = 0;
    }

    // ---------------------------------------------------------------------------
    // Convenience helpers (must be called while holding lock)
    // ---------------------------------------------------------------------------

    /** Returns the player whose turn it currently is, or empty if the room has no players. */
    public Optional<Player> currentPlayer() {
        if (players.isEmpty()) return Optional.empty();
        return Optional.of(players.get(currentTurnIndex % players.size()));
    }

    /** Advances the turn to the next player in a round-robin fashion. */
    public void advanceTurn() {
        currentTurnIndex = (currentTurnIndex + 1) % players.size();
        lastActivityTime = LocalDateTime.now();
    }

    /** Returns the top card of the discard pile, or empty if the pile is empty. */
    public Optional<Card> topDiscard() {
        if (discardPile.isEmpty()) return Optional.empty();
        return Optional.of(discardPile.getLast());
    }

    /** Whether there is still room for at least one more player. */
    public boolean hasSpace() {
        return players.size() < MAX_PLAYERS;
    }

    /** Finds a player by their stable player ID. */
    public Optional<Player> findByPlayerId(String playerId) {
        return players.stream().filter(p -> p.getPlayerId().equals(playerId)).findFirst();
    }

    /** Finds a player by their current WebSocket session ID. */
    public Optional<Player> findBySessionId(String sessionId) {
        return players.stream().filter(p -> sessionId.equals(p.getSessionId())).findFirst();
    }

    /** Returns all cards from the discard pile except the top card (for reshuffling). */
    public List<Card> drainDiscardExceptTop() {
        if (discardPile.size() <= 1) return new ArrayList<>();
        Card top = discardPile.removeLast();        // keep top card
        List<Card> drained = new ArrayList<>(discardPile);
        discardPile.clear();
        discardPile.addLast(top);                   // put top back
        return drained;
    }

    @Override
    public String toString() {
        return "GameRoom{id=" + roomId + ", state=" + roomState + ", players=" + players.size() + "}";
    }
}


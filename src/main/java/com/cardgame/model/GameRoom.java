package com.cardgame.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Central state container for one game room.
 *
 * <h3>Thread-safety contract</h3>
 * All mutations MUST be performed while holding {@link #getLock()}.
 * The player list uses {@link CopyOnWriteArrayList} so it can be iterated
 * (for broadcasts) without the lock, while structural changes still happen
 * inside the lock.
 */
@Getter
@Setter
public class GameRoom {

    public static final int MAX_PLAYERS   = 4;
    public static final int CARDS_PER_HAND = 13;

    // ---------------------------------------------------------------------------
    // Identity
    // ---------------------------------------------------------------------------

    private final String roomId;
    /** Short human-readable code shown in the UI (e.g. "AB12CD"). */
    private final String roomCode;

    /** PlayerId of the player who created this room (can start the game). */
    private volatile String creatorId;

    // ---------------------------------------------------------------------------
    // Players
    // ---------------------------------------------------------------------------

    private final List<Player> players = new CopyOnWriteArrayList<>();

    // ---------------------------------------------------------------------------
    // Game state
    // ---------------------------------------------------------------------------

    private volatile RoomStatus status;

    /** The draw pile — top of deck is the last element. */
    private final Deck deck;

    /**
     * Discard pile — top is the last element (Deque used as stack).
     * Never null; starts empty.
     */
    private final Deque<Card> discardPile = new ArrayDeque<>();

    /**
     * Per-player discard history — playerId → list of discarded cards (newest first).
     * Separate from the main discard pile; used for display purposes.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.LinkedList<Card>>
            playerDiscardHistory = new java.util.concurrent.ConcurrentHashMap<>();

    /** Index into {@code players} for the current turn. */
    private volatile int currentTurnIndex;

    /**
     * True when the current turn player has already drawn a card
     * (so they must discard or declare win next).
     */
    private volatile boolean currentPlayerHasDrawn;

    // ---------------------------------------------------------------------------
    // Joker
    // ---------------------------------------------------------------------------

    /**
     * Whether the Joker is active.
     * Becomes true the moment a valid 4-card set is confirmed (during DECLARE_WIN).
     */
    private volatile boolean jokerUnlocked;

    // ---------------------------------------------------------------------------
    // Winner
    // ---------------------------------------------------------------------------

    private volatile String winnerId;
    private volatile String winnerName;

    // ---------------------------------------------------------------------------
    // Timestamps
    // ---------------------------------------------------------------------------

    private final LocalDateTime createdTime;
    private volatile LocalDateTime lastActivityTime;

    // ---------------------------------------------------------------------------
    // Concurrency
    // ---------------------------------------------------------------------------

    /** Fair reentrant lock — one lock per room prevents race conditions. */
    private final ReentrantLock lock = new ReentrantLock(true);

    // ---------------------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------------------

    public GameRoom(String roomId, String roomCode) {
        this.roomId           = roomId;
        this.roomCode         = roomCode;
        this.status           = RoomStatus.WAITING_FOR_PLAYERS;
        this.deck             = new Deck();
        this.createdTime      = LocalDateTime.now();
        this.lastActivityTime = LocalDateTime.now();
    }

    // ---------------------------------------------------------------------------
    // Helpers (must be called while holding lock)
    // ---------------------------------------------------------------------------

    public Optional<Player> currentPlayer() {
        if (players.isEmpty()) return Optional.empty();
        return Optional.of(players.get(currentTurnIndex % players.size()));
    }

    public void advanceTurn() {
        currentTurnIndex     = (currentTurnIndex + 1) % players.size();
        currentPlayerHasDrawn = false;
        lastActivityTime     = LocalDateTime.now();
    }

    public Optional<Card> peekTopDiscard() {
        return discardPile.isEmpty() ? Optional.empty() : Optional.of(discardPile.peek());
    }

    public Optional<Player> findByPlayerId(String playerId) {
        return players.stream().filter(p -> p.getPlayerId().equals(playerId)).findFirst();
    }

    public Optional<Player> findBySessionId(String sessionId) {
        return players.stream().filter(p -> sessionId.equals(p.getSessionId())).findFirst();
    }

    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    /** Returns true if enough players are present to start the game (2+). */
    public boolean canStart() {
        return players.size() >= 2 && status == RoomStatus.WAITING_FOR_PLAYERS;
    }

    /**
     * Adds a card to a player's discard history.
     * Keeps at most 20 entries per player.
     */
    public void recordDiscard(String playerId, Card card) {
        playerDiscardHistory.compute(playerId, (id, list) -> {
            if (list == null) list = new java.util.LinkedList<>();
            list.addFirst(card);
            if (list.size() > 20) list.removeLast();
            return list;
        });
    }

    /**
     * Returns a snapshot of each player's discard history (newest first, max 10 per player).
     */
    public java.util.Map<String, List<Card>> getPlayerDiscardSnapshot() {
        java.util.Map<String, List<Card>> snapshot = new java.util.LinkedHashMap<>();
        for (Player p : players) {
            java.util.LinkedList<Card> hist = playerDiscardHistory.get(p.getPlayerId());
            snapshot.put(p.getPlayerId(), hist == null ? List.of() : List.copyOf(hist));
        }
        return snapshot;
    }

    public boolean isActive() {
        return status == RoomStatus.PLAYING || status == RoomStatus.JOKER_UNLOCKED || status == RoomStatus.DEALING;
    }

    public List<String> connectedSessionIds() {
        List<String> ids = new ArrayList<>();
        for (Player p : players) {
            if (p.isConnected() && p.getSessionId() != null) {
                ids.add(p.getSessionId());
            }
        }
        return ids;
    }

    @Override
    public String toString() {
        return "GameRoom{code=" + roomCode + ", status=" + status + ", players=" + players.size() + "}";
    }
}


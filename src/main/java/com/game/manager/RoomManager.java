package com.game.manager;

import com.game.exception.GameAlreadyStartedException;
import com.game.exception.RoomFullException;
import com.game.exception.RoomNotFoundException;
import com.game.model.GameRoom;
import com.game.model.Player;
import com.game.model.RoomState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of all game rooms.
 *
 * <h3>Concurrency</h3>
 * Room-level operations acquire the room's {@link java.util.concurrent.locks.ReentrantLock}
 * before mutating room state.  The room map itself is a {@link ConcurrentHashMap}, so
 * room creation/deletion are safe without additional locking.
 *
 * <h3>Memory</h3>
 * All game state lives in-RAM only.  The cleanup scheduler (see {@code CleanupService})
 * calls {@link #removeInactiveRooms()} on a fixed schedule to evict stale rooms.
 */
@Service
public class RoomManager {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    /** All active rooms, keyed by room ID. */
    private final ConcurrentHashMap<String, GameRoom> rooms = new ConcurrentHashMap<>(1024);

    /**
     * Bidirectional lookup helpers.
     * These maps are kept consistent whenever a session joins or leaves a room.
     */
    private final ConcurrentHashMap<String, String> sessionToRoomId   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToPlayerId = new ConcurrentHashMap<>();

    @Value("${game.room-inactive-minutes:30}")
    private int roomInactiveMinutes;

    @Value("${game.max-rooms:1000}")
    private int maxRooms;

    // ---------------------------------------------------------------------------
    // Room CRUD
    // ---------------------------------------------------------------------------

    /**
     * Creates a new room and adds the creating player.
     *
     * @param sessionId  WebSocket session of the creator.
     * @param playerName Display name chosen by the creator.
     * @return The newly created {@link GameRoom}.
     */
    public GameRoom createRoom(String sessionId, String playerName) {
        if (rooms.size() >= maxRooms) {
            throw new IllegalStateException("Server has reached the maximum number of concurrent rooms (" + maxRooms + ")");
        }

        String roomId   = generateRoomId();
        String playerId = generatePlayerId();

        GameRoom room   = new GameRoom(roomId);
        Player   player = new Player(playerId, playerName, sessionId);

        room.getLock().lock();
        try {
            room.getPlayers().add(player);
        } finally {
            room.getLock().unlock();
        }

        rooms.put(roomId, room);
        registerSession(sessionId, roomId, playerId);

        log.info("Room [{}] created by player [{}] (session={})", roomId, playerName, sessionId);
        return room;
    }

    /**
     * Adds a player to an existing room.
     * If {@code playerId} is provided and matches an existing disconnected player,
     * the call is treated as a <em>reconnect</em> and the session is reattached.
     *
     * @param sessionId  WebSocket session of the joining player.
     * @param roomId     Target room ID.
     * @param playerName Display name.
     * @param playerId   Optional: stable player ID for reconnect support.
     * @return The player that joined or reconnected.
     */
    public Player joinRoom(String sessionId, String roomId, String playerName, String playerId) {
        GameRoom room = getRoom(roomId);

        room.getLock().lock();
        try {
            // --- Reconnect path ---
            if (playerId != null) {
                Optional<Player> existing = room.findByPlayerId(playerId);
                if (existing.isPresent()) {
                    Player p = existing.get();
                    String oldSession = p.getSessionId();

                    p.setSessionId(sessionId);
                    p.setConnected(true);
                    p.setLastSeen(LocalDateTime.now());

                    // Clean up old session mappings
                    if (oldSession != null) {
                        sessionToRoomId.remove(oldSession);
                        sessionToPlayerId.remove(oldSession);
                    }
                    registerSession(sessionId, roomId, p.getPlayerId());

                    room.setLastActivityTime(LocalDateTime.now());
                    log.info("Player [{}] reconnected to room [{}] (newSession={})", p.getPlayerName(), roomId, sessionId);
                    return p;
                }
            }

            // --- New-join path ---
            if (room.getRoomState() != RoomState.WAITING) {
                throw new GameAlreadyStartedException(roomId);
            }
            if (!room.hasSpace()) {
                throw new RoomFullException(roomId);
            }

            String newPlayerId = generatePlayerId();
            Player newPlayer   = new Player(newPlayerId, playerName, sessionId);
            room.getPlayers().add(newPlayer);
            room.setLastActivityTime(LocalDateTime.now());

            registerSession(sessionId, roomId, newPlayerId);

            log.info("Player [{}] joined room [{}] (session={})", playerName, roomId, sessionId);
            return newPlayer;

        } finally {
            room.getLock().unlock();
        }
    }

    /**
     * Removes a player from a room (voluntary leave).
     * If the room becomes empty it is removed from the map.
     */
    public void leaveRoom(String sessionId) {
        String roomId = sessionToRoomId.get(sessionId);
        if (roomId == null) return;

        GameRoom room = rooms.get(roomId);
        if (room == null) return;

        room.getLock().lock();
        try {
            room.findBySessionId(sessionId).ifPresent(player -> {
                room.getPlayers().remove(player);
                log.info("Player [{}] left room [{}]", player.getPlayerName(), roomId);

                // If the current turn belongs to the leaving player, advance it
                if (!room.getPlayers().isEmpty() && room.getRoomState() == RoomState.STARTED) {
                    room.setCurrentTurnIndex(room.getCurrentTurnIndex() % room.getPlayers().size());
                }
            });

            if (room.getPlayers().isEmpty()) {
                rooms.remove(roomId);
                log.info("Room [{}] removed (empty after player left)", roomId);
            }
        } finally {
            room.getLock().unlock();
        }

        deregisterSession(sessionId);
    }

    /**
     * Called on WebSocket disconnect — marks the player as disconnected
     * but does NOT remove them from the room (they may reconnect).
     */
    public void handleDisconnect(String sessionId) {
        String roomId = sessionToRoomId.get(sessionId);
        if (roomId == null) return;

        GameRoom room = rooms.get(roomId);
        if (room == null) return;

        room.getLock().lock();
        try {
            room.findBySessionId(sessionId).ifPresent(player -> {
                player.setConnected(false);
                player.setLastSeen(LocalDateTime.now());
                log.info("Player [{}] disconnected from room [{}]", player.getPlayerName(), roomId);
            });
        } finally {
            room.getLock().unlock();
        }

        // Keep session→room mapping for potential reconnect, but remove after a grace period
        // (actual cleanup is done by CleanupService)
    }

    // ---------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------

    /**
     * Returns the room or throws {@link RoomNotFoundException}.
     */
    public GameRoom getRoom(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RoomNotFoundException(roomId);
        }
        return room;
    }

    public Optional<GameRoom> findRoomBySessionId(String sessionId) {
        String roomId = sessionToRoomId.get(sessionId);
        if (roomId == null) return Optional.empty();
        return Optional.ofNullable(rooms.get(roomId));
    }

    public Optional<String> getPlayerIdBySessionId(String sessionId) {
        return Optional.ofNullable(sessionToPlayerId.get(sessionId));
    }

    public Collection<GameRoom> getAllRooms() {
        return rooms.values();
    }

    public int activeRoomCount() {
        return rooms.size();
    }

    // ---------------------------------------------------------------------------
    // Start game (delegates to caller to invoke GameEngine.dealCards)
    // ---------------------------------------------------------------------------

    /**
     * Validates that the game can be started and transitions state to STARTED.
     * The caller must subsequently invoke {@code GameEngine.dealCards(room)}.
     *
     * @param roomId    Room to start.
     * @param sessionId Session of the player requesting start (must be the owner).
     */
    public GameRoom startGame(String roomId, String sessionId) {
        GameRoom room = getRoom(roomId);

        room.getLock().lock();
        try {
            if (room.getRoomState() != RoomState.WAITING) {
                throw new GameAlreadyStartedException(roomId);
            }
            if (room.getPlayers().size() < 2) {
                throw new IllegalStateException("Need at least 2 players to start the game");
            }
            // Only the room owner (index 0) can start
            Player owner = room.getPlayers().get(0);
            if (!owner.getSessionId().equals(sessionId)) {
                throw new IllegalStateException("Only the room owner can start the game");
            }

            room.setRoomState(RoomState.STARTED);
            room.setLastActivityTime(LocalDateTime.now());

            log.info("Game started in room [{}] with {} players", roomId, room.getPlayers().size());
            return room;

        } finally {
            room.getLock().unlock();
        }
    }

    // ---------------------------------------------------------------------------
    // Cleanup
    // ---------------------------------------------------------------------------

    /**
     * Removes rooms that have been inactive longer than the configured threshold.
     * Called by the scheduled {@code CleanupService}.
     */
    public void removeInactiveRooms() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(roomInactiveMinutes);
        rooms.entrySet().removeIf(entry -> {
            GameRoom room = entry.getValue();
            boolean stale = room.getLastActivityTime().isBefore(cutoff);
            if (stale) {
                log.info("Removing inactive room [{}] (last activity: {})",
                        room.getRoomId(), room.getLastActivityTime());
                // Clean up all session mappings for this room
                room.getPlayers().forEach(p -> deregisterSession(p.getSessionId()));
            }
            return stale;
        });
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private void registerSession(String sessionId, String roomId, String playerId) {
        sessionToRoomId.put(sessionId, roomId);
        sessionToPlayerId.put(sessionId, playerId);
    }

    private void deregisterSession(String sessionId) {
        sessionToRoomId.remove(sessionId);
        sessionToPlayerId.remove(sessionId);
    }

    private String generateRoomId() {
        // Short, uppercase, URL-friendly ID
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String generatePlayerId() {
        return UUID.randomUUID().toString();
    }
}


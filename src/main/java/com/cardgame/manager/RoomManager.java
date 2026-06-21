package com.cardgame.manager;

import com.cardgame.exception.GameException;
import com.cardgame.exception.RoomNotFoundException;
import com.cardgame.model.GameRoom;
import com.cardgame.model.Player;
import com.cardgame.model.RoomStatus;
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
 * In-memory registry of all active game rooms.
 *
 * <h3>Storage</h3>
 * <ul>
 *   <li>{@code rooms} — roomId → GameRoom</li>
 *   <li>{@code codeToId} — short room code → roomId (for player join lookup)</li>
 *   <li>{@code sessionToRoomId} — WS sessionId → roomId</li>
 *   <li>{@code sessionToPlayerId} — WS sessionId → playerId</li>
 * </ul>
 * All maps are {@link ConcurrentHashMap} for lock-free lookup.
 * Mutations to the room itself are guarded by the room's {@link java.util.concurrent.locks.ReentrantLock}.
 */
@Service
public class RoomManager {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    private final ConcurrentHashMap<String, GameRoom> rooms           = new ConcurrentHashMap<>(1024);
    private final ConcurrentHashMap<String, String>   codeToId        = new ConcurrentHashMap<>(1024);
    private final ConcurrentHashMap<String, String>   sessionToRoomId  = new ConcurrentHashMap<>(4096);
    private final ConcurrentHashMap<String, String>   sessionToPlayerId = new ConcurrentHashMap<>(4096);

    @Value("${game.room-inactive-minutes:30}")
    private int roomInactiveMinutes;

    @Value("${game.max-rooms:1000}")
    private int maxRooms;

    // ---------------------------------------------------------------------------
    // Create
    // ---------------------------------------------------------------------------

    public GameRoom createRoom(String sessionId, String playerName) {
        if (rooms.size() >= maxRooms) {
            throw new GameException("Server at capacity — try again later.");
        }
        String roomId   = UUID.randomUUID().toString();
        String roomCode = generateRoomCode();
        String playerId = UUID.randomUUID().toString();

        GameRoom room   = new GameRoom(roomId, roomCode);
        Player   player = new Player(playerId, playerName, sessionId, 0);

        room.getLock().lock();
        try {
            room.getPlayers().add(player);
        } finally {
            room.getLock().unlock();
        }

        rooms.put(roomId, room);
        codeToId.put(roomCode, roomId);
        bindSession(sessionId, roomId, playerId);

        log.info("Room [{}] created by {} ({})", roomCode, playerName, sessionId);
        return room;
    }

    // ---------------------------------------------------------------------------
    // Join / Reconnect
    // ---------------------------------------------------------------------------

    /**
     * Joins an existing room or reconnects a previously-joined player.
     *
     * @param playerId  Supply to reconnect; null for a fresh join.
     * @return the Player who joined/reconnected.
     */
    public Player joinRoom(String sessionId, String roomCode, String playerName, String playerId) {
        GameRoom room = getRoomByCode(roomCode);

        room.getLock().lock();
        try {
            // --- Reconnect path ---
            if (playerId != null) {
                Optional<Player> existing = room.findByPlayerId(playerId);
                if (existing.isPresent()) {
                    Player p = existing.get();
                    unbindSession(p.getSessionId());
                    p.setSessionId(sessionId);
                    p.setConnected(true);
                    p.setLastSeen(LocalDateTime.now());
                    bindSession(sessionId, room.getRoomId(), p.getPlayerId());
                    room.setLastActivityTime(LocalDateTime.now());
                    log.info("Player {} reconnected to room [{}]", p.getPlayerName(), roomCode);
                    return p;
                }
            }

            // --- New join path ---
            if (room.getStatus() != RoomStatus.WAITING_FOR_PLAYERS) {
                throw new GameException("Room [" + roomCode + "] is no longer accepting players.");
            }
            if (room.isFull()) {
                throw new GameException("Room [" + roomCode + "] is full.");
            }

            String newPlayerId = UUID.randomUUID().toString();
            int    seatIndex   = room.getPlayers().size();
            Player newPlayer   = new Player(newPlayerId, playerName, sessionId, seatIndex);
            room.getPlayers().add(newPlayer);
            room.setLastActivityTime(LocalDateTime.now());
            bindSession(sessionId, room.getRoomId(), newPlayerId);

            log.info("Player {} joined room [{}] (seat {})", playerName, roomCode, seatIndex);
            return newPlayer;
        } finally {
            room.getLock().unlock();
        }
    }

    // ---------------------------------------------------------------------------
    // Disconnect / Leave
    // ---------------------------------------------------------------------------

    /** Marks the player as disconnected (keeps them in the room for reconnect). */
    public void handleDisconnect(String sessionId) {
        String roomId = sessionToRoomId.get(sessionId);
        if (roomId == null) return;
        GameRoom room = rooms.get(roomId);
        if (room == null) return;

        room.getLock().lock();
        try {
            room.findBySessionId(sessionId).ifPresent(p -> {
                p.setConnected(false);
                p.setLastSeen(LocalDateTime.now());
                log.info("Player {} disconnected from room [{}]", p.getPlayerName(), room.getRoomCode());
            });
        } finally {
            room.getLock().unlock();
        }
        // Keep session binding for reconnect grace period
    }

    /** Voluntarily removes a player from a room. */
    public void leaveRoom(String sessionId) {
        String roomId = sessionToRoomId.get(sessionId);
        if (roomId == null) return;
        GameRoom room = rooms.get(roomId);
        if (room == null) return;

        room.getLock().lock();
        try {
            room.findBySessionId(sessionId).ifPresent(p -> {
                room.getPlayers().remove(p);
                log.info("Player {} left room [{}]", p.getPlayerName(), room.getRoomCode());
            });
            if (room.getPlayers().isEmpty()) {
                rooms.remove(roomId);
                codeToId.remove(room.getRoomCode());
                log.info("Room [{}] removed (empty)", room.getRoomCode());
            }
        } finally {
            room.getLock().unlock();
        }
        unbindSession(sessionId);
    }

    // ---------------------------------------------------------------------------
    // Lookups
    // ---------------------------------------------------------------------------

    public GameRoom getRoom(String roomId) {
        GameRoom r = rooms.get(roomId);
        if (r == null) throw new RoomNotFoundException(roomId);
        return r;
    }

    public GameRoom getRoomByCode(String roomCode) {
        String id = codeToId.get(roomCode.toUpperCase());
        if (id == null) throw new RoomNotFoundException(roomCode);
        return getRoom(id);
    }

    public Optional<GameRoom> findRoomBySession(String sessionId) {
        String roomId = sessionToRoomId.get(sessionId);
        if (roomId == null) return Optional.empty();
        return Optional.ofNullable(rooms.get(roomId));
    }

    public Optional<String> getPlayerIdBySession(String sessionId) {
        return Optional.ofNullable(sessionToPlayerId.get(sessionId));
    }

    public Collection<GameRoom> getAllRooms() { return rooms.values(); }

    public int activeRoomCount() { return rooms.size(); }

    // ---------------------------------------------------------------------------
    // Cleanup
    // ---------------------------------------------------------------------------

    public void removeInactiveRooms() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(roomInactiveMinutes);
        rooms.entrySet().removeIf(e -> {
            GameRoom r = e.getValue();
            if (r.getLastActivityTime().isBefore(cutoff)) {
                codeToId.remove(r.getRoomCode());
                r.getPlayers().forEach(p -> unbindSession(p.getSessionId()));
                log.info("Removed stale room [{}]", r.getRoomCode());
                return true;
            }
            return false;
        });
    }

    // ---------------------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------------------

    private void bindSession(String sessionId, String roomId, String playerId) {
        if (sessionId != null) {
            sessionToRoomId.put(sessionId, roomId);
            sessionToPlayerId.put(sessionId, playerId);
        }
    }

    private void unbindSession(String sessionId) {
        if (sessionId != null) {
            sessionToRoomId.remove(sessionId);
            sessionToPlayerId.remove(sessionId);
        }
    }

    /** Generates a short, memorable 6-character room code. */
    private String generateRoomCode() {
        for (int attempt = 0; attempt < 100; attempt++) {
            String code = UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 6)
                    .toUpperCase();
            if (!codeToId.containsKey(code)) return code;
        }
        throw new GameException("Could not generate a unique room code.");
    }
}


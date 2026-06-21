package com.game.controller;

import com.game.dto.request.CreateRoomRequest;
import com.game.dto.request.JoinRoomRequest;
import com.game.dto.response.PlayerInfo;
import com.game.dto.response.RoomResponse;
import com.game.manager.RoomManager;
import com.game.model.GameRoom;
import com.game.model.Player;
import com.game.service.GameEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API for room management.
 *
 * <p>These endpoints are primarily for HTTP clients, tooling, and service-to-service
 * integrations.  Real-time in-game actions (PLAY_CARD, DRAW_CARD, etc.) are
 * handled exclusively over WebSocket.</p>
 *
 * <pre>
 * POST  /rooms              – create a new room
 * GET   /rooms/{id}         – get room info
 * POST  /rooms/{id}/join    – join an existing room
 * POST  /rooms/{id}/start   – start the game (room owner only)
 * GET   /rooms              – list all active rooms (admin/debug)
 * </pre>
 */
@RestController
@RequestMapping("/rooms")
public class RoomController {

    private static final Logger log = LoggerFactory.getLogger(RoomController.class);

    private final RoomManager roomManager;
    private final GameEngine  gameEngine;

    public RoomController(RoomManager roomManager, GameEngine gameEngine) {
        this.roomManager = roomManager;
        this.gameEngine  = gameEngine;
    }

    // ---------------------------------------------------------------------------
    // POST /rooms
    // ---------------------------------------------------------------------------

    /**
     * Creates a new game room.
     *
     * <p>In this REST flow the caller identifies themselves via the
     * {@code X-Session-Id} header (the same session ID their WebSocket
     * connection uses).  This ties the HTTP call to their live WS session.</p>
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createRoom(
            @RequestBody CreateRoomRequest request,
            @RequestHeader(value = "X-Session-Id", required = false, defaultValue = "http-session") String sessionId) {

        log.info("REST createRoom — playerName={}, session={}", request.playerName(), sessionId);

        GameRoom room   = roomManager.createRoom(sessionId, request.playerName());
        Player   player = room.getPlayers().get(0);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "roomId",   room.getRoomId(),
                "playerId", player.getPlayerId(),
                "room",     buildRoomResponse(room)
        ));
    }

    // ---------------------------------------------------------------------------
    // GET /rooms/{id}
    // ---------------------------------------------------------------------------

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable("id") String roomId) {
        GameRoom room = roomManager.getRoom(roomId);   // throws RoomNotFoundException → 404
        return ResponseEntity.ok(buildRoomResponse(room));
    }

    // ---------------------------------------------------------------------------
    // POST /rooms/{id}/join
    // ---------------------------------------------------------------------------

    @PostMapping("/{id}/join")
    public ResponseEntity<Map<String, Object>> joinRoom(
            @PathVariable("id") String roomId,
            @RequestBody JoinRoomRequest request,
            @RequestHeader(value = "X-Session-Id", required = false, defaultValue = "http-session") String sessionId) {

        log.info("REST joinRoom — roomId={}, playerName={}, session={}", roomId, request.playerName(), sessionId);

        Player   player = roomManager.joinRoom(sessionId, roomId, request.playerName(), request.playerId());
        GameRoom room   = roomManager.getRoom(roomId);

        return ResponseEntity.ok(Map.of(
                "playerId", player.getPlayerId(),
                "roomId",   roomId,
                "room",     buildRoomResponse(room)
        ));
    }

    // ---------------------------------------------------------------------------
    // POST /rooms/{id}/start
    // ---------------------------------------------------------------------------

    @PostMapping("/{id}/start")
    public ResponseEntity<RoomResponse> startGame(
            @PathVariable("id") String roomId,
            @RequestHeader(value = "X-Session-Id", required = false, defaultValue = "http-session") String sessionId) {

        log.info("REST startGame — roomId={}, session={}", roomId, sessionId);

        GameRoom room = roomManager.startGame(roomId, sessionId);
        gameEngine.dealCards(room);

        return ResponseEntity.ok(buildRoomResponse(room));
    }

    // ---------------------------------------------------------------------------
    // GET /rooms  (debug / admin)
    // ---------------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<Map<String, Object>> listRooms() {
        List<RoomResponse> all = roomManager.getAllRooms().stream()
                .map(this::buildRoomResponse)
                .toList();

        return ResponseEntity.ok(Map.of(
                "count", all.size(),
                "rooms", all
        ));
    }

    // ---------------------------------------------------------------------------
    // DTO builder (duplicates the one in GameWebSocketHandler — extract to a
    // shared mapper bean if the project grows)
    // ---------------------------------------------------------------------------

    private RoomResponse buildRoomResponse(GameRoom room) {
        Optional<Player> current = room.currentPlayer();
        String currentPlayerId   = current.map(Player::getPlayerId).orElse(null);

        List<PlayerInfo> playerInfos = room.getPlayers().stream()
                .map(p -> new PlayerInfo(
                        p.getPlayerId(),
                        p.getPlayerName(),
                        p.handSize(),
                        p.isConnected(),
                        p.getPlayerId().equals(currentPlayerId)
                ))
                .toList();

        boolean canStart = room.getRoomState() == com.game.model.RoomState.WAITING
                && room.getPlayers().size() >= 2;

        return new RoomResponse(
                room.getRoomId(),
                room.getRoomState(),
                playerInfos,
                GameRoom.MAX_PLAYERS,
                canStart
        );
    }
}


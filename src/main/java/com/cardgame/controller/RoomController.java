package com.cardgame.controller;

import com.cardgame.dto.response.PlayerSummary;
import com.cardgame.manager.RoomManager;
import com.cardgame.model.GameRoom;
import com.cardgame.model.Player;
import com.cardgame.model.RoomStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST endpoints for room querying and health checks.
 * Game actions are handled exclusively over WebSocket.
 *
 * <pre>
 * GET  /api/rooms/{code}   – get room summary
 * GET  /api/rooms          – list all rooms (admin/debug)
 * GET  /api/health         – health check
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class RoomController {

    private final RoomManager roomManager;

    public RoomController(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @GetMapping("/rooms/{code}")
    public ResponseEntity<Map<String, Object>> getRoom(@PathVariable("code") String code) {
        GameRoom room = roomManager.getRoomByCode(code);
        return ResponseEntity.ok(Map.of(
                "roomCode",    room.getRoomCode(),
                "status",      room.getStatus(),
                "playerCount", room.getPlayers().size(),
                "maxPlayers",  GameRoom.MAX_PLAYERS,
                "players",     buildSummaries(room)
        ));
    }

    @GetMapping("/rooms")
    public ResponseEntity<Map<String, Object>> listRooms() {
        List<Map<String, Object>> list = roomManager.getAllRooms().stream()
                .map(r -> Map.of(
                        "roomCode",    (Object) r.getRoomCode(),
                        "status",      r.getStatus(),
                        "playerCount", r.getPlayers().size()
                ))
                .toList();
        return ResponseEntity.ok(Map.of("count", list.size(), "rooms", list));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status",      "UP",
                "activeRooms", roomManager.activeRoomCount()
        ));
    }

    private List<PlayerSummary> buildSummaries(GameRoom room) {
        Optional<Player> current  = room.currentPlayer();
        String currentId = current.map(Player::getPlayerId).orElse(null);
        return room.getPlayers().stream()
                .map(p -> new PlayerSummary(
                        p.getPlayerId(), p.getPlayerName(), p.getSeatIndex(),
                        p.handSize(), p.isConnected(),
                        p.getPlayerId().equals(currentId)
                ))
                .toList();
    }
}


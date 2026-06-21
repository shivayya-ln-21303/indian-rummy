package com.game.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.dto.request.*;
import com.game.dto.response.GameStateResponse;
import com.game.dto.response.PlayerInfo;
import com.game.dto.response.RoomResponse;
import com.game.dto.response.WebSocketResponse;
import com.game.exception.GameException;
import com.game.manager.RoomManager;
import com.game.model.Card;
import com.game.model.GameRoom;
import com.game.model.Player;
import com.game.service.GameEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Optional;

/**
 * Primary WebSocket handler — receives all inbound messages and routes them
 * to the appropriate service, then broadcasts results back to the room.
 *
 * <h3>Protocol</h3>
 * Every message is a JSON object with a mandatory {@code type} field and an
 * optional {@code payload} object:
 * <pre>
 * { "type": "PLAY_CARD", "payload": { "cardIndex": 2 } }
 * </pre>
 *
 * <h3>Supported client → server message types</h3>
 * <ul>
 *   <li>{@code CREATE_ROOM} – payload: {@code { playerName }}</li>
 *   <li>{@code JOIN_ROOM}   – payload: {@code { roomId, playerName [, playerId] }}</li>
 *   <li>{@code START_GAME}  – no payload required</li>
 *   <li>{@code PLAY_CARD}   – payload: {@code { cardIndex }}</li>
 *   <li>{@code DRAW_CARD}   – no payload required</li>
 *   <li>{@code LEAVE_ROOM}  – no payload required</li>
 * </ul>
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final RoomManager          roomManager;
    private final GameEngine           gameEngine;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper         objectMapper;

    public GameWebSocketHandler(RoomManager roomManager,
                                GameEngine gameEngine,
                                WebSocketSessionManager sessionManager,
                                ObjectMapper objectMapper) {
        this.roomManager    = roomManager;
        this.gameEngine     = gameEngine;
        this.sessionManager = sessionManager;
        this.objectMapper   = objectMapper;
    }

    // ---------------------------------------------------------------------------
    // Connection lifecycle
    // ---------------------------------------------------------------------------

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.registerSession(session);
        log.info("WebSocket connection established: session={}", session.getId());
        sendToSession(session, WebSocketResponse.success("CONNECTED",
                java.util.Map.of("sessionId", session.getId())));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket connection closed: session={}, status={}", session.getId(), status);
        sessionManager.removeSession(session.getId());
        roomManager.handleDisconnect(session.getId());

        // Notify remaining players that someone disconnected
        roomManager.findRoomBySessionId(session.getId()).ifPresent(room ->
                broadcastRoomState(room, "PLAYER_DISCONNECTED"));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error on session={}: {}", session.getId(), exception.getMessage());
    }

    // ---------------------------------------------------------------------------
    // Message routing
    // ---------------------------------------------------------------------------

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage rawMessage) {
        String sessionId = session.getId();
        log.debug("Received message from session={}: {}", sessionId, rawMessage.getPayload());

        try {
            WebSocketMessage msg = objectMapper.readValue(rawMessage.getPayload(), WebSocketMessage.class);

            switch (msg.type()) {
                case "CREATE_ROOM"  -> handleCreateRoom(session, msg);
                case "JOIN_ROOM"    -> handleJoinRoom(session, msg);
                case "START_GAME"   -> handleStartGame(session);
                case "PLAY_CARD"    -> handlePlayCard(session, msg);
                case "DRAW_CARD"    -> handleDrawCard(session);
                case "LEAVE_ROOM"   -> handleLeaveRoom(session);
                default             -> sendToSession(session,
                        WebSocketResponse.error("UNKNOWN_MESSAGE_TYPE",
                                "Unknown message type: " + msg.type()));
            }

        } catch (GameException ex) {
            log.warn("Game exception for session={}: {}", sessionId, ex.getMessage());
            sendToSession(session, WebSocketResponse.error("ERROR", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Unexpected error for session={}: {}", sessionId, ex.getMessage(), ex);
            sendToSession(session, WebSocketResponse.error("ERROR", "Internal server error"));
        }
    }

    // ---------------------------------------------------------------------------
    // Handlers
    // ---------------------------------------------------------------------------

    private void handleCreateRoom(WebSocketSession session, WebSocketMessage msg) throws Exception {
        CreateRoomRequest req = objectMapper.treeToValue(msg.payload(), CreateRoomRequest.class);
        validateNotNull(req.playerName(), "playerName");

        GameRoom room   = roomManager.createRoom(session.getId(), req.playerName());
        Player   player = room.getPlayers().get(0);

        sendToSession(session, WebSocketResponse.success("ROOM_CREATED",
                java.util.Map.of(
                        "roomId",   room.getRoomId(),
                        "playerId", player.getPlayerId(),
                        "room",     buildRoomResponse(room)
                )));

        log.info("Room [{}] created by session={}", room.getRoomId(), session.getId());
    }

    private void handleJoinRoom(WebSocketSession session, WebSocketMessage msg) throws Exception {
        JoinRoomRequest req = objectMapper.treeToValue(msg.payload(), JoinRoomRequest.class);
        validateNotNull(req.roomId(), "roomId");

        boolean isReconnect = req.playerId() != null;
        Player player = roomManager.joinRoom(session.getId(), req.roomId(), req.playerName(), req.playerId());
        GameRoom room = roomManager.getRoom(req.roomId());

        // Tell the (re)joining player their identity and current state
        sendToSession(session, WebSocketResponse.success(isReconnect ? "RECONNECTED" : "JOINED",
                java.util.Map.of(
                        "playerId", player.getPlayerId(),
                        "roomId",  room.getRoomId(),
                        "state",   buildGameState(room, player)
                )));

        // Notify everyone else
        broadcastToRoom(room, session.getId(),
                WebSocketResponse.success("PLAYER_JOINED",
                        java.util.Map.of(
                                "playerName", player.getPlayerName(),
                                "room",       buildRoomResponse(room)
                        )));
    }

    private void handleStartGame(WebSocketSession session) throws Exception {
        GameRoom room = requireRoomForSession(session);
        roomManager.startGame(room.getRoomId(), session.getId());
        gameEngine.dealCards(room);

        // Send personalised state to each player (each sees only their own hand)
        broadcastPersonalisedState(room, "GAME_STARTED");
    }

    private void handlePlayCard(WebSocketSession session, WebSocketMessage msg) throws Exception {
        PlayCardRequest req = objectMapper.treeToValue(msg.payload(), PlayCardRequest.class);
        GameRoom room   = requireRoomForSession(session);
        String playerId = requirePlayerId(session);

        Card played = gameEngine.playCard(room, playerId, req.cardIndex());

        // Broadcast updated state (personalised hand per player)
        boolean gameOver = room.getRoomState() == com.game.model.RoomState.FINISHED;
        broadcastPersonalisedState(room, gameOver ? "GAME_OVER" : "CARD_PLAYED");

        if (gameOver) {
            log.info("Game over in room [{}] — winner: {}", room.getRoomId(), room.getWinnerId());
        }
    }

    private void handleDrawCard(WebSocketSession session) throws Exception {
        GameRoom room   = requireRoomForSession(session);
        String playerId = requirePlayerId(session);

        Card drawn = gameEngine.drawCard(room, playerId);

        // The drawing player is told which card they got; everyone else just sees counts update
        broadcastPersonalisedState(room, "CARD_DRAWN");
    }

    private void handleLeaveRoom(WebSocketSession session) {
        roomManager.findRoomBySessionId(session.getId()).ifPresent(room -> {
            broadcastToRoom(room, session.getId(),
                    WebSocketResponse.success("PLAYER_LEFT",
                            java.util.Map.of("room", buildRoomResponse(room))));
        });
        roomManager.leaveRoom(session.getId());
        sendToSession(session, WebSocketResponse.success("LEFT_ROOM", null));
    }

    // ---------------------------------------------------------------------------
    // Broadcast helpers
    // ---------------------------------------------------------------------------

    /**
     * Sends a personalised game-state snapshot to every connected player.
     * Each player sees their own hand cards; others only see card counts.
     */
    private void broadcastPersonalisedState(GameRoom room, String eventType) {
        for (Player player : room.getPlayers()) {
            if (!player.isConnected()) continue;
            GameStateResponse state = buildGameState(room, player);
            sessionManager.sendToSession(player.getSessionId(),
                    toJson(WebSocketResponse.success(eventType, state)));
        }
    }

    /**
     * Broadcasts the same message to all players in a room except the sender.
     */
    private void broadcastToRoom(GameRoom room, String excludeSessionId, WebSocketResponse response) {
        String json = toJson(response);
        for (Player player : room.getPlayers()) {
            if (player.getSessionId() != null
                    && !player.getSessionId().equals(excludeSessionId)
                    && player.isConnected()) {
                sessionManager.sendToSession(player.getSessionId(), json);
            }
        }
    }

    /**
     * Broadcasts a generic (non-personalised) room-state update to all connected players.
     */
    private void broadcastRoomState(GameRoom room, String eventType) {
        String json = toJson(WebSocketResponse.success(eventType, buildRoomResponse(room)));
        for (Player player : room.getPlayers()) {
            if (player.isConnected() && player.getSessionId() != null) {
                sessionManager.sendToSession(player.getSessionId(), json);
            }
        }
    }

    // ---------------------------------------------------------------------------
    // DTO builders
    // ---------------------------------------------------------------------------

    /**
     * Builds a personalised {@link GameStateResponse} for a specific player.
     * Only that player's {@code yourHand} is populated.
     */
    private GameStateResponse buildGameState(GameRoom room, Player recipient) {
        Optional<Player> current = room.currentPlayer();
        String currentPlayerId   = current.map(Player::getPlayerId).orElse(null);
        String currentPlayerName = current.map(Player::getPlayerName).orElse(null);

        List<PlayerInfo> playerInfos = room.getPlayers().stream()
                .map(p -> new PlayerInfo(
                        p.getPlayerId(),
                        p.getPlayerName(),
                        p.handSize(),
                        p.isConnected(),
                        p.getPlayerId().equals(currentPlayerId)
                ))
                .toList();

        return new GameStateResponse(
                room.getRoomId(),
                room.getRoomState(),
                playerInfos,
                List.copyOf(recipient.getHandCards()),   // personalised
                room.topDiscard().orElse(null),
                currentPlayerId,
                currentPlayerName,
                room.getDeck().size(),
                room.getWinnerId()
        );
    }

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

    // ---------------------------------------------------------------------------
    // Utility helpers
    // ---------------------------------------------------------------------------

    private void sendToSession(WebSocketSession session, WebSocketResponse response) {
        sessionManager.sendToSession(session.getId(), toJson(response));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialise response: {}", e.getMessage(), e);
            return "{\"type\":\"ERROR\",\"success\":false,\"error\":\"Serialisation failure\"}";
        }
    }

    private GameRoom requireRoomForSession(WebSocketSession session) {
        return roomManager.findRoomBySessionId(session.getId())
                .orElseThrow(() -> new GameException("You are not in any room"));
    }

    private String requirePlayerId(WebSocketSession session) {
        return roomManager.getPlayerIdBySessionId(session.getId())
                .orElseThrow(() -> new GameException("Player identity unknown for this session"));
    }

    private void validateNotNull(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}


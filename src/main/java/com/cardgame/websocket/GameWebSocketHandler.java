package com.cardgame.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cardgame.dto.request.*;
import com.cardgame.dto.response.GameStateResponse;
import com.cardgame.dto.response.PlayerSummary;
import com.cardgame.dto.response.WebSocketResponse;
import com.cardgame.exception.GameException;
import com.cardgame.manager.RoomManager;
import com.cardgame.model.*;
import com.cardgame.service.GameEngine;
import com.cardgame.service.TurnTimerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;

/**
 * Primary WebSocket handler for Indian Rummy.
 *
 * <h3>Supported client → server messages</h3>
 * <table>
 *   <tr><th>Type</th><th>Payload</th></tr>
 *   <tr><td>CREATE_ROOM</td><td>{ playerName }</td></tr>
 *   <tr><td>JOIN_ROOM</td><td>{ roomCode, playerName [, playerId] }</td></tr>
 *   <tr><td>RECONNECT</td><td>{ roomCode, playerId }</td></tr>
 *   <tr><td>DRAW_CARD</td><td>{} (draws from deck)</td></tr>
 *   <tr><td>DRAW_FROM_DISCARD</td><td>{}</td></tr>
 *   <tr><td>DISCARD_CARD</td><td>{ cardId }</td></tr>
 *   <tr><td>REARRANGE_CARDS</td><td>{ groups: [[cardId...]] }</td></tr>
 *   <tr><td>DECLARE_WIN</td><td>{ groups: [[cardId...]], discardCardId? }</td></tr>
 * </table>
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final RoomManager          roomManager;
    private final GameEngine           gameEngine;
    private final WebSocketSessionManager sessionManager;
    private final TurnTimerService     timerService;
    private final ObjectMapper         objectMapper;

    @Value("${game.turn-timeout-seconds:30}")
    private int turnTimeoutSeconds;

    public GameWebSocketHandler(RoomManager roomManager,
                                GameEngine gameEngine,
                                WebSocketSessionManager sessionManager,
                                TurnTimerService timerService,
                                ObjectMapper objectMapper) {
        this.roomManager    = roomManager;
        this.gameEngine     = gameEngine;
        this.sessionManager = sessionManager;
        this.timerService   = timerService;
        this.objectMapper   = objectMapper;
    }

    // =========================================================================
    // Connection lifecycle
    // =========================================================================

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.register(session);
        log.info("WS connected: {}", session.getId());
        send(session, WebSocketResponse.ok("CONNECTED", Map.of("sessionId", session.getId())));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.remove(session.getId());
        roomManager.findRoomBySession(session.getId()).ifPresent(room -> {
            roomManager.handleDisconnect(session.getId());
            broadcastRoomUpdate(room, "PLAYER_DISCONNECTED");
        });
        log.info("WS closed: {} ({})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.error("WS transport error session={}: {}", session.getId(), ex.getMessage());
    }

    // =========================================================================
    // Message routing
    // =========================================================================

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage raw) {
        try {
            WebSocketMessage msg = objectMapper.readValue(raw.getPayload(), WebSocketMessage.class);
            route(session, msg);
        } catch (GameException ex) {
            log.warn("GameException session={}: {}", session.getId(), ex.getMessage());
            send(session, WebSocketResponse.err("ERROR", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Unexpected error session={}", session.getId(), ex);
            send(session, WebSocketResponse.err("ERROR", "Internal server error"));
        }
    }

    private void route(WebSocketSession session, WebSocketMessage msg) throws Exception {
        switch (msg.type()) {
            case "CREATE_ROOM"        -> handleCreateRoom(session, msg);
            case "JOIN_ROOM"          -> handleJoinRoom(session, msg);
            case "RECONNECT"          -> handleReconnect(session, msg);
            case "DRAW_CARD"          -> handleDrawFromDeck(session);
            case "DRAW_FROM_DISCARD"  -> handleDrawFromDiscard(session);
            case "DISCARD_CARD"       -> handleDiscard(session, msg);
            case "REARRANGE_CARDS"    -> handleRearrange(session, msg);
            case "DECLARE_WIN"        -> handleDeclareWin(session, msg);
            default -> send(session, WebSocketResponse.err("ERROR", "Unknown message type: " + msg.type()));
        }
    }

    // =========================================================================
    // Handlers
    // =========================================================================

    private void handleCreateRoom(WebSocketSession session, WebSocketMessage msg) throws Exception {
        CreateRoomRequest req = parse(msg, CreateRoomRequest.class);
        notBlank(req.playerName(), "playerName");

        GameRoom room   = roomManager.createRoom(session.getId(), req.playerName());
        Player   player = room.getPlayers().get(0);

        send(session, WebSocketResponse.ok("ROOM_CREATED", Map.of(
                "roomCode", room.getRoomCode(),
                "roomId",   room.getRoomId(),
                "playerId", player.getPlayerId()
        )));
    }

    private void handleJoinRoom(WebSocketSession session, WebSocketMessage msg) throws Exception {
        JoinRoomRequest req = parse(msg, JoinRoomRequest.class);
        notBlank(req.roomCode(), "roomCode");

        Player   player = roomManager.joinRoom(session.getId(), req.roomCode(), req.playerName(), req.playerId());
        GameRoom room   = roomManager.getRoomByCode(req.roomCode());

        // Tell the joining player their identity + current state
        send(session, WebSocketResponse.ok("PLAYER_JOINED", Map.of(
                "playerId",   player.getPlayerId(),
                "roomCode",   room.getRoomCode(),
                "playerName", player.getPlayerName(),
                "gameState",  buildState(room, player)
        )));

        // Notify everyone else
        broadcastExcept(room, session.getId(),
                WebSocketResponse.ok("PLAYER_JOINED", Map.of(
                        "playerName",  player.getPlayerName(),
                        "playerCount", room.getPlayers().size()
                )));

        // Auto-start when room is full
        if (room.isFull() && room.getStatus() == RoomStatus.WAITING_FOR_PLAYERS) {
            startGame(room);
        }
    }

    private void handleReconnect(WebSocketSession session, WebSocketMessage msg) throws Exception {
        JoinRoomRequest req = parse(msg, JoinRoomRequest.class);
        notBlank(req.roomCode(), "roomCode");
        notBlank(req.playerId(), "playerId");

        Player   player = roomManager.joinRoom(session.getId(), req.roomCode(), null, req.playerId());
        GameRoom room   = roomManager.getRoomByCode(req.roomCode());

        send(session, WebSocketResponse.ok("RECONNECTED", Map.of(
                "playerId",  player.getPlayerId(),
                "gameState", buildState(room, player)
        )));
        broadcastExcept(room, session.getId(),
                WebSocketResponse.ok("PLAYER_RECONNECTED", Map.of("playerName", player.getPlayerName())));
    }

    private void handleDrawFromDeck(WebSocketSession session) {
        GameRoom room     = requireRoom(session);
        String   playerId = requirePlayerId(session);

        timerService.cancel(room.getRoomId());
        Card drawn = gameEngine.drawFromDeck(room, playerId);

        // Tell only the drawing player which card they got
        send(session, WebSocketResponse.ok("CARD_DRAWN", Map.of(
                "card",        drawn,
                "deckSize",    room.getDeck().size(),
                "mustDiscard", true
        )));
        // Tell others that deck was drawn (no card reveal)
        broadcastExcept(room, session.getId(),
                WebSocketResponse.ok("CARD_DRAWN", Map.of(
                        "playerId",  playerId,
                        "deckSize",  room.getDeck().size()
                )));
        restartTimer(room);
    }

    private void handleDrawFromDiscard(WebSocketSession session) {
        GameRoom room     = requireRoom(session);
        String   playerId = requirePlayerId(session);

        timerService.cancel(room.getRoomId());
        Card taken    = gameEngine.drawFromDiscard(room, playerId);
        Card newTop   = room.peekTopDiscard().orElse(null);

        // Drawing from discard is public — everyone sees it
        broadcastAll(room, WebSocketResponse.ok("CARD_DRAWN_FROM_DISCARD", Map.of(
                "playerId",   playerId,
                "card",       taken,
                "newTopDiscard", newTop != null ? newTop : "empty",
                "deckSize",   room.getDeck().size()
        )));

        // Extra: tell the drawing player the card details (already in broadcast but confirm)
        restartTimer(room);
    }

    private void handleDiscard(WebSocketSession session, WebSocketMessage msg) throws Exception {
        DiscardCardRequest req = parse(msg, DiscardCardRequest.class);
        notBlank(req.cardId(), "cardId");

        GameRoom room     = requireRoom(session);
        String   playerId = requirePlayerId(session);

        timerService.cancel(room.getRoomId());
        Card     discarded  = gameEngine.discardCard(room, playerId, req.cardId());
        Player   nextPlayer = room.currentPlayer().orElse(null);

        broadcastAll(room, WebSocketResponse.ok("CARD_DISCARDED", Map.of(
                "playerId",       playerId,
                "card",           discarded,
                "nextPlayerId",   nextPlayer != null ? nextPlayer.getPlayerId() : "",
                "nextPlayerName", nextPlayer != null ? nextPlayer.getPlayerName() : ""
        )));

        // Broadcast updated player summaries (card counts changed)
        broadcastPlayerSummaries(room, "TURN_CHANGED");

        startTurnTimer(room);
    }

    private void handleRearrange(WebSocketSession session, WebSocketMessage msg) throws Exception {
        RearrangeCardsRequest req = parse(msg, RearrangeCardsRequest.class);
        GameRoom room     = requireRoom(session);
        String   playerId = requirePlayerId(session);

        gameEngine.rearrangeCards(room, playerId, req.groups());
        send(session, WebSocketResponse.ok("CARDS_REARRANGED", Map.of("groups", req.groups())));
    }

    private void handleDeclareWin(WebSocketSession session, WebSocketMessage msg) throws Exception {
        DeclareWinRequest req = parse(msg, DeclareWinRequest.class);
        GameRoom room     = requireRoom(session);
        String   playerId = requirePlayerId(session);

        timerService.cancel(room.getRoomId());

        boolean won = gameEngine.declareWin(room, playerId, req.groups(), req.discardCardId());

        if (won) {
            broadcastAll(room, WebSocketResponse.ok("PLAYER_WON", Map.of(
                    "winnerId",   room.getWinnerId(),
                    "winnerName", room.getWinnerName()
            )));
        } else {
            // Should not happen — declareWin throws on failure, returns true on success
            send(session, WebSocketResponse.err("DECLARE_WIN_FAILED", "Invalid declaration."));
        }
    }

    // =========================================================================
    // Game start (auto-triggered when room fills)
    // =========================================================================

    private void startGame(GameRoom room) {
        room.getLock().lock();
        try {
            if (room.getStatus() != RoomStatus.WAITING_FOR_PLAYERS) return;
            room.setStatus(RoomStatus.DEALING);
        } finally {
            room.getLock().unlock();
        }

        broadcastAll(room, WebSocketResponse.ok("GAME_STARTED", Map.of(
                "roomCode", room.getRoomCode(),
                "players",  buildSummaries(room, null)
        )));

        // Deal cards — sends personalised CARD_DISTRIBUTED to each player
        Map<String, List<Card>> dealt = gameEngine.dealCards(room);

        dealt.forEach((pid, cards) -> {
            room.findByPlayerId(pid).ifPresent(player -> {
                if (player.getSessionId() != null) {
                    sessionManager.send(player.getSessionId(),
                            toJson(WebSocketResponse.ok("CARD_DISTRIBUTED", Map.of(
                                    "cards",         cards,
                                    "deckSize",      room.getDeck().size(),
                                    "topDiscard",    room.peekTopDiscard().orElse(null)
                            ))));
                }
            });
        });

        // Announce first turn
        broadcastPlayerSummaries(room, "TURN_CHANGED");
        startTurnTimer(room);
    }

    // =========================================================================
    // Timer helpers
    // =========================================================================

    private void startTurnTimer(GameRoom room) {
        timerService.start(room.getRoomId(), turnTimeoutSeconds, () -> {
            if (!room.isActive()) return;
            Card autoDiscard = gameEngine.autoAdvanceTurn(room);
            broadcastAll(room, WebSocketResponse.ok("TURN_TIMEOUT", Map.of(
                    "autoDiscard", autoDiscard != null ? autoDiscard : "none"
            )));
            broadcastPlayerSummaries(room, "TURN_CHANGED");
            startTurnTimer(room);
        });
    }

    private void restartTimer(GameRoom room) {
        startTurnTimer(room);
    }

    // =========================================================================
    // Broadcast helpers
    // =========================================================================

    private void broadcastAll(GameRoom room, WebSocketResponse response) {
        String json = toJson(response);
        sessionManager.broadcast(room.connectedSessionIds(), json);
    }

    private void broadcastExcept(GameRoom room, String excludeSession, WebSocketResponse response) {
        String json = toJson(response);
        for (Player p : room.getPlayers()) {
            if (p.isConnected() && p.getSessionId() != null
                    && !p.getSessionId().equals(excludeSession)) {
                sessionManager.send(p.getSessionId(), json);
            }
        }
    }

    private void broadcastRoomUpdate(GameRoom room, String type) {
        broadcastAll(room, WebSocketResponse.ok(type, Map.of(
                "players", buildSummaries(room, null)
        )));
    }

    private void broadcastPlayerSummaries(GameRoom room, String type) {
        Optional<Player> current = room.currentPlayer();
        String currentId = current.map(Player::getPlayerId).orElse(null);
        List<PlayerSummary> summaries = buildSummaries(room, currentId);
        long timeLeft = timerService.remainingSeconds(room.getRoomId());
        broadcastAll(room, WebSocketResponse.ok(type, Map.of(
                "players",        summaries,
                "currentPlayerId", currentId != null ? currentId : "",
                "timeLeft",       timeLeft
        )));
    }

    // =========================================================================
    // DTO builders
    // =========================================================================

    private GameStateResponse buildState(GameRoom room, Player recipient) {
        Optional<Player> current = room.currentPlayer();
        String currentId   = current.map(Player::getPlayerId).orElse(null);
        long   timeLeft    = timerService.remainingSeconds(room.getRoomId());

        return new GameStateResponse(
                room.getRoomId(),
                room.getRoomCode(),
                room.getStatus(),
                buildSummaries(room, currentId),
                List.copyOf(recipient.getHandCards()),
                List.copyOf(recipient.getGroups()),
                room.peekTopDiscard().orElse(null),
                room.getDeck().size(),
                currentId,
                current.map(Player::getPlayerName).orElse(null),
                room.isJokerUnlocked(),
                (int) timeLeft,
                room.getWinnerId(),
                room.getWinnerName()
        );
    }

    private List<PlayerSummary> buildSummaries(GameRoom room, String currentPlayerId) {
        return room.getPlayers().stream()
                .map(p -> new PlayerSummary(
                        p.getPlayerId(),
                        p.getPlayerName(),
                        p.getSeatIndex(),
                        p.handSize(),
                        p.isConnected(),
                        p.getPlayerId().equals(currentPlayerId)
                ))
                .toList();
    }

    // =========================================================================
    // Utility helpers
    // =========================================================================

    private void send(WebSocketSession session, WebSocketResponse response) {
        sessionManager.send(session.getId(), toJson(response));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON serialisation failed", e);
            return "{\"type\":\"ERROR\",\"success\":false,\"error\":\"Serialisation failure\"}";
        }
    }

    private <T> T parse(WebSocketMessage msg, Class<T> clazz) throws Exception {
        if (msg.payload() == null) {
            return clazz.getDeclaredConstructor().newInstance(); // default instance
        }
        return objectMapper.treeToValue(msg.payload(), clazz);
    }

    private GameRoom requireRoom(WebSocketSession session) {
        return roomManager.findRoomBySession(session.getId())
                .orElseThrow(() -> new GameException("You are not in any room."));
    }

    private String requirePlayerId(WebSocketSession session) {
        return roomManager.getPlayerIdBySession(session.getId())
                .orElseThrow(() -> new GameException("Player identity unknown."));
    }

    private void notBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new GameException(field + " must not be blank.");
        }
    }
}


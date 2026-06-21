package com.game.manager;

import com.game.exception.GameAlreadyStartedException;
import com.game.exception.RoomFullException;
import com.game.exception.RoomNotFoundException;
import com.game.model.GameRoom;
import com.game.model.Player;
import com.game.model.RoomState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link RoomManager}.
 * No Spring context — all @Value fields injected via ReflectionTestUtils.
 */
class RoomManagerTest {

    private RoomManager roomManager;

    @BeforeEach
    void setUp() {
        roomManager = new RoomManager();
        ReflectionTestUtils.setField(roomManager, "roomInactiveMinutes", 30);
        ReflectionTestUtils.setField(roomManager, "maxRooms", 1000);
    }

    // ---------------------------------------------------------------------------
    // createRoom
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("createRoom returns a room with one player")
    void createRoomReturnsRoomWithOnePlayer() {
        GameRoom room = roomManager.createRoom("session-1", "Alice");

        assertThat(room).isNotNull();
        assertThat(room.getRoomId()).isNotBlank();
        assertThat(room.getPlayers()).hasSize(1);
        assertThat(room.getPlayers().get(0).getPlayerName()).isEqualTo("Alice");
        assertThat(room.getRoomState()).isEqualTo(RoomState.WAITING);
    }

    @Test
    @DisplayName("createRoom increments active room count")
    void createRoomIncrementsCount() {
        int before = roomManager.activeRoomCount();
        roomManager.createRoom("s1", "Alice");
        assertThat(roomManager.activeRoomCount()).isEqualTo(before + 1);
    }

    // ---------------------------------------------------------------------------
    // joinRoom
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("joinRoom adds a second player to an existing room")
    void joinRoomAddsPlayer() {
        GameRoom room   = roomManager.createRoom("s1", "Alice");
        Player   player = roomManager.joinRoom("s2", room.getRoomId(), "Bob", null);

        assertThat(player.getPlayerName()).isEqualTo("Bob");
        assertThat(room.getPlayers()).hasSize(2);
    }

    @Test
    @DisplayName("joinRoom throws RoomFullException when room is at capacity")
    void joinRoomThrowsWhenFull() {
        GameRoom room = roomManager.createRoom("s0", "P0");
        roomManager.joinRoom("s1", room.getRoomId(), "P1", null);
        roomManager.joinRoom("s2", room.getRoomId(), "P2", null);
        roomManager.joinRoom("s3", room.getRoomId(), "P3", null);

        assertThatThrownBy(() ->
                roomManager.joinRoom("s4", room.getRoomId(), "P4", null))
                .isInstanceOf(RoomFullException.class);
    }

    @Test
    @DisplayName("joinRoom throws RoomNotFoundException for unknown room")
    void joinRoomThrowsForUnknownRoom() {
        assertThatThrownBy(() ->
                roomManager.joinRoom("s1", "NONEXISTENT", "Alice", null))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    @DisplayName("joinRoom with playerId reconnects the player")
    void joinRoomReconnectsPlayer() {
        GameRoom room     = roomManager.createRoom("old-session", "Alice");
        String   playerId = room.getPlayers().get(0).getPlayerId();

        // Simulate disconnect
        roomManager.handleDisconnect("old-session");
        assertThat(room.getPlayers().get(0).isConnected()).isFalse();

        // Reconnect with new session
        Player reconnected = roomManager.joinRoom("new-session", room.getRoomId(), "Alice", playerId);

        assertThat(reconnected.getPlayerId()).isEqualTo(playerId);
        assertThat(reconnected.isConnected()).isTrue();
        assertThat(reconnected.getSessionId()).isEqualTo("new-session");
    }

    // ---------------------------------------------------------------------------
    // getRoom
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getRoom throws RoomNotFoundException for unknown room")
    void getRoomThrowsForUnknown() {
        assertThatThrownBy(() -> roomManager.getRoom("UNKNOWN"))
                .isInstanceOf(RoomNotFoundException.class);
    }

    // ---------------------------------------------------------------------------
    // leaveRoom
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("leaveRoom removes player and cleans up empty room")
    void leaveRoomRemovesPlayerAndRoom() {
        GameRoom room = roomManager.createRoom("s1", "Alice");
        String roomId = room.getRoomId();
        int beforeCount = roomManager.activeRoomCount();

        roomManager.leaveRoom("s1");

        assertThat(roomManager.activeRoomCount()).isEqualTo(beforeCount - 1);
        assertThatThrownBy(() -> roomManager.getRoom(roomId))
                .isInstanceOf(RoomNotFoundException.class);
    }

    // ---------------------------------------------------------------------------
    // startGame
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("startGame transitions room to STARTED")
    void startGameTransitionsState() {
        GameRoom room = roomManager.createRoom("s1", "Alice");
        roomManager.joinRoom("s2", room.getRoomId(), "Bob", null);

        roomManager.startGame(room.getRoomId(), "s1");

        assertThat(room.getRoomState()).isEqualTo(RoomState.STARTED);
    }

    @Test
    @DisplayName("startGame throws when only one player is in the room")
    void startGameThrowsWithOnePlayer() {
        GameRoom room = roomManager.createRoom("s1", "Alice");

        assertThatThrownBy(() -> roomManager.startGame(room.getRoomId(), "s1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 2");
    }

    @Test
    @DisplayName("startGame throws GameAlreadyStartedException on double-start")
    void startGameThrowsOnDoubleStart() {
        GameRoom room = roomManager.createRoom("s1", "Alice");
        roomManager.joinRoom("s2", room.getRoomId(), "Bob", null);
        roomManager.startGame(room.getRoomId(), "s1");

        assertThatThrownBy(() -> roomManager.startGame(room.getRoomId(), "s1"))
                .isInstanceOf(GameAlreadyStartedException.class);
    }

    // ---------------------------------------------------------------------------
    // handleDisconnect
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("handleDisconnect marks player as disconnected but keeps them in room")
    void handleDisconnectMarksPlayerDisconnected() {
        GameRoom room = roomManager.createRoom("s1", "Alice");

        roomManager.handleDisconnect("s1");

        assertThat(room.getPlayers()).hasSize(1);
        assertThat(room.getPlayers().get(0).isConnected()).isFalse();
    }

    // ---------------------------------------------------------------------------
    // removeInactiveRooms
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("removeInactiveRooms does not remove recently-active rooms")
    void removeInactiveRoomsKeepsActiveRooms() {
        GameRoom room = roomManager.createRoom("s1", "Alice");
        int before = roomManager.activeRoomCount();

        roomManager.removeInactiveRooms();

        assertThat(roomManager.activeRoomCount()).isEqualTo(before);
        assertThatCode(() -> roomManager.getRoom(room.getRoomId())).doesNotThrowAnyException();
    }
}


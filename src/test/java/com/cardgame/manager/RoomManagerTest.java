package com.cardgame.manager;

import com.cardgame.exception.GameException;
import com.cardgame.exception.RoomNotFoundException;
import com.cardgame.model.GameRoom;
import com.cardgame.model.Player;
import com.cardgame.model.RoomStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class RoomManagerTest {

    private RoomManager manager;

    @BeforeEach void setup() {
        manager = new RoomManager();
        ReflectionTestUtils.setField(manager, "roomInactiveMinutes", 30);
        ReflectionTestUtils.setField(manager, "maxRooms", 1000);
    }

    @Test @DisplayName("createRoom produces a room with 1 player and unique code")
    void createRoomBasic() {
        GameRoom room = manager.createRoom("s1", "Alice");
        assertThat(room).isNotNull();
        assertThat(room.getRoomCode()).hasSize(6);
        assertThat(room.getPlayers()).hasSize(1);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING_FOR_PLAYERS);
    }

    @Test @DisplayName("joinRoom adds up to 4 players")
    void joinRoomAddsPlayers() {
        GameRoom room = manager.createRoom("s1", "Alice");
        manager.joinRoom("s2", room.getRoomCode(), "Bob", null);
        manager.joinRoom("s3", room.getRoomCode(), "Carl", null);
        manager.joinRoom("s4", room.getRoomCode(), "Dana", null);
        assertThat(room.getPlayers()).hasSize(4);
    }

    @Test @DisplayName("joinRoom throws when room is full")
    void joinRoomFull() {
        GameRoom room = manager.createRoom("s1", "A");
        manager.joinRoom("s2", room.getRoomCode(), "B", null);
        manager.joinRoom("s3", room.getRoomCode(), "C", null);
        manager.joinRoom("s4", room.getRoomCode(), "D", null);
        assertThatThrownBy(() -> manager.joinRoom("s5", room.getRoomCode(), "E", null))
                .isInstanceOf(GameException.class).hasMessageContaining("full");
    }

    @Test @DisplayName("getRoomByCode throws for unknown code")
    void getUnknownRoom() {
        assertThatThrownBy(() -> manager.getRoomByCode("ZZZZZZ"))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test @DisplayName("handleDisconnect marks player disconnected, keeps them in room")
    void handleDisconnect() {
        GameRoom room = manager.createRoom("s1", "Alice");
        manager.handleDisconnect("s1");
        assertThat(room.getPlayers()).hasSize(1);
        assertThat(room.getPlayers().get(0).isConnected()).isFalse();
    }

    @Test @DisplayName("joinRoom reconnects a disconnected player with same playerId")
    void reconnect() {
        GameRoom room = manager.createRoom("old-session", "Alice");
        String playerId = room.getPlayers().get(0).getPlayerId();
        manager.handleDisconnect("old-session");

        Player reconnected = manager.joinRoom("new-session", room.getRoomCode(), "Alice", playerId);

        assertThat(reconnected.getPlayerId()).isEqualTo(playerId);
        assertThat(reconnected.isConnected()).isTrue();
        assertThat(reconnected.getSessionId()).isEqualTo("new-session");
    }

    @Test @DisplayName("leaveRoom removes player and deletes empty room")
    void leaveRoom() {
        GameRoom room = manager.createRoom("s1", "Alice");
        String code   = room.getRoomCode();
        manager.leaveRoom("s1");
        assertThatThrownBy(() -> manager.getRoomByCode(code))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test @DisplayName("findRoomBySession returns room when session is active")
    void findRoomBySession() {
        GameRoom room = manager.createRoom("s1", "Alice");
        assertThat(manager.findRoomBySession("s1")).contains(room);
    }

    @Test @DisplayName("findRoomBySession returns empty for unknown session")
    void findRoomBySessionUnknown() {
        assertThat(manager.findRoomBySession("unknown")).isEmpty();
    }

    @Test @DisplayName("removeInactiveRooms keeps recently-active rooms")
    void keepActiveRooms() {
        manager.createRoom("s1", "Alice");
        int before = manager.activeRoomCount();
        manager.removeInactiveRooms();
        assertThat(manager.activeRoomCount()).isEqualTo(before);
    }

    @Test @DisplayName("Two rooms get different codes")
    void uniqueRoomCodes() {
        GameRoom r1 = manager.createRoom("s1", "A");
        GameRoom r2 = manager.createRoom("s2", "B");
        assertThat(r1.getRoomCode()).isNotEqualTo(r2.getRoomCode());
    }
}


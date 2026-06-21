package com.game.service;

import com.game.exception.InvalidMoveException;
import com.game.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GameEngine}.
 * No Spring context — the engine is instantiated directly.
 */
class GameEngineTest {

    private GameEngine gameEngine;

    @BeforeEach
    void setUp() {
        gameEngine = new GameEngine();
        // Inject @Value fields manually (no Spring context)
        ReflectionTestUtils.setField(gameEngine, "initialHandSize", 7);
    }

    // ---------------------------------------------------------------------------
    // dealCards
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("dealCards gives each player 7 cards and places 1 on discard")
    void dealCardsDistributesCorrectly() {
        GameRoom room = roomWith4Players();
        room.setRoomState(RoomState.STARTED);

        gameEngine.dealCards(room);

        // Each player should have 7 cards
        room.getPlayers().forEach(p ->
                assertThat(p.handSize()).isEqualTo(7));

        // Discard pile should have exactly 1 card
        assertThat(room.getDiscardPile()).hasSize(1);

        // Deck should have 52 - (4*7 + 1) = 23 cards remaining
        assertThat(room.getDeck().size()).isEqualTo(52 - (4 * 7 + 1));
    }

    // ---------------------------------------------------------------------------
    // isValidPlay
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Card matching suit is valid")
    void samesuItIsValid() {
        Card topDiscard = new Card(Suit.HEARTS, Rank.FIVE);
        Card toPlay     = new Card(Suit.HEARTS, Rank.KING);
        assertThat(gameEngine.isValidPlay(toPlay, topDiscard)).isTrue();
    }

    @Test
    @DisplayName("Card matching rank is valid")
    void sameRankIsValid() {
        Card topDiscard = new Card(Suit.HEARTS,   Rank.FIVE);
        Card toPlay     = new Card(Suit.DIAMONDS, Rank.FIVE);
        assertThat(gameEngine.isValidPlay(toPlay, topDiscard)).isTrue();
    }

    @Test
    @DisplayName("Card with different suit and rank is invalid")
    void differentSuitAndRankIsInvalid() {
        Card topDiscard = new Card(Suit.HEARTS,   Rank.FIVE);
        Card toPlay     = new Card(Suit.DIAMONDS, Rank.SEVEN);
        assertThat(gameEngine.isValidPlay(toPlay, topDiscard)).isFalse();
    }

    // ---------------------------------------------------------------------------
    // playCard
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("playCard removes card from hand and adds to discard pile")
    void playCardCommitsMove() {
        GameRoom room = singlePlayerStartedRoom();

        Player player = room.getPlayers().get(0);
        Card topDiscard = room.topDiscard().orElseThrow();

        // Find a playable card
        Card playable = player.getHandCards().stream()
                .filter(c -> gameEngine.isValidPlay(c, topDiscard))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No playable card in hand (test setup issue)"));

        int indexToPlay = player.getHandCards().indexOf(playable);
        int handSizeBefore  = player.handSize();
        int discardBefore   = room.getDiscardPile().size();

        gameEngine.playCard(room, player.getPlayerId(), indexToPlay);

        assertThat(player.handSize()).isEqualTo(handSizeBefore - 1);
        assertThat(room.getDiscardPile()).hasSize(discardBefore + 1);
        assertThat(room.topDiscard()).hasValue(playable);
    }

    @Test
    @DisplayName("playCard throws InvalidMoveException when it is not the player's turn")
    void playCardWrongTurnThrows() {
        GameRoom room = roomWith2PlayersStarted();

        // Player at index 1 tries to move when it's index 0's turn
        Player wrongPlayer = room.getPlayers().get(1);

        assertThatThrownBy(() ->
                gameEngine.playCard(room, wrongPlayer.getPlayerId(), 0))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("not your turn");
    }

    @Test
    @DisplayName("playCard throws InvalidMoveException for unplayable card")
    void playCardInvalidCardThrows() {
        GameRoom room = singlePlayerStartedRoom();
        Player player = room.getPlayers().get(0);

        // Force a known top of discard and fill hand with non-matching cards
        room.getDiscardPile().clear();
        room.getDiscardPile().addLast(new Card(Suit.HEARTS, Rank.ACE));
        player.getHandCards().clear();
        player.getHandCards().add(new Card(Suit.SPADES, Rank.TWO));   // neither suit nor rank matches

        assertThatThrownBy(() ->
                gameEngine.playCard(room, player.getPlayerId(), 0))
                .isInstanceOf(InvalidMoveException.class);
    }

    // ---------------------------------------------------------------------------
    // drawCard
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("drawCard adds a card to the player's hand and advances the turn")
    void drawCardAddsToHand() {
        GameRoom room = roomWith2PlayersStarted();

        Player current = room.currentPlayer().orElseThrow();
        int handBefore = current.handSize();

        gameEngine.drawCard(room, current.getPlayerId());

        assertThat(current.handSize()).isEqualTo(handBefore + 1);
        // Turn should have advanced to the next player
        assertThat(room.currentPlayer().orElseThrow().getPlayerId())
                .isNotEqualTo(current.getPlayerId());
    }

    @Test
    @DisplayName("drawCard triggers reshuffle when deck is empty")
    void drawCardReshufflesWhenDeckEmpty() {
        GameRoom room = singlePlayerStartedRoom();
        Player player = room.getPlayers().get(0);

        // Exhaust the deck
        while (!room.getDeck().isEmpty()) {
            room.getDeck().draw();
        }

        // Fill discard with several cards (leaving the top one)
        room.getDiscardPile().clear();
        room.getDiscardPile().addLast(new Card(Suit.HEARTS, Rank.ACE));
        for (Rank r : List.of(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE)) {
            room.getDiscardPile().addLast(new Card(Suit.CLUBS, r));
        }

        // Should succeed (reshuffle happens internally)
        assertThatNoException().isThrownBy(() ->
                gameEngine.drawCard(room, player.getPlayerId()));
    }

    // ---------------------------------------------------------------------------
    // Win detection
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Playing last card transitions room to FINISHED and sets winnerId")
    void playLastCardWins() {
        GameRoom room   = singlePlayerStartedRoom();
        Player   player = room.getPlayers().get(0);

        // Give the player exactly 1 card that matches the top of discard
        Card topDiscard = room.topDiscard().orElseThrow();
        player.getHandCards().clear();
        player.getHandCards().add(new Card(topDiscard.suit(), Rank.TWO));

        gameEngine.playCard(room, player.getPlayerId(), 0);

        assertThat(room.getRoomState()).isEqualTo(RoomState.FINISHED);
        assertThat(room.getWinnerId()).isEqualTo(player.getPlayerId());
    }

    // ---------------------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------------------

    private GameRoom roomWith4Players() {
        GameRoom room = new GameRoom("ROOM-TEST");
        for (int i = 0; i < 4; i++) {
            room.getPlayers().add(new Player("p" + i, "Player" + i, "session" + i));
        }
        return room;
    }

    private GameRoom roomWith2PlayersStarted() {
        GameRoom room = new GameRoom("ROOM-2P");
        room.getPlayers().add(new Player("p0", "Alice", "s0"));
        room.getPlayers().add(new Player("p1", "Bob",   "s1"));
        room.setRoomState(RoomState.STARTED);
        gameEngine.dealCards(room);
        return room;
    }

    /**
     * 1-player room — useful for testing move logic without turn-order complexity.
     */
    private GameRoom singlePlayerStartedRoom() {
        GameRoom room = new GameRoom("ROOM-1P");
        room.getPlayers().add(new Player("p0", "Solo", "s0"));
        room.setRoomState(RoomState.STARTED);
        gameEngine.dealCards(room);
        return room;
    }
}


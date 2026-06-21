package com.cardgame.service;

import com.cardgame.exception.InvalidMoveException;
import com.cardgame.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class GameEngineTest {

    private GameEngine engine;

    @BeforeEach void setup() {
        engine = new GameEngine();
        ReflectionTestUtils.setField(engine, "cardsPerPlayer", 13);
    }

    // -----------------------------------------------------------------------
    // dealCards
    // -----------------------------------------------------------------------

    @Test @DisplayName("dealCards gives each of 4 players exactly 13 cards")
    void dealCardsDistribution() {
        GameRoom room = make4PlayerRoom();
        room.setStatus(RoomStatus.PLAYING);
        engine.dealCards(room);
        room.getPlayers().forEach(p -> assertThat(p.handSize()).isEqualTo(13));
    }

    @Test @DisplayName("dealCards leaves 52 cards in draw pile (105 - 52 dealt - 1 discard)")
    void dealCardsDrawPileSize() {
        GameRoom room = make4PlayerRoom();
        room.setStatus(RoomStatus.PLAYING);
        engine.dealCards(room);
        assertThat(room.getDeck().size()).isEqualTo(52); // 105 - 52 - 1
    }

    @Test @DisplayName("dealCards places one card on discard pile")
    void dealCardsDiscard() {
        GameRoom room = make4PlayerRoom();
        room.setStatus(RoomStatus.PLAYING);
        engine.dealCards(room);
        assertThat(room.getDiscardPile()).hasSize(1);
    }

    @Test @DisplayName("dealCards result contains all dealt cards per player")
    void dealCardsResult() {
        GameRoom room = make4PlayerRoom();
        room.setStatus(RoomStatus.PLAYING);
        Map<String, List<Card>> dealt = engine.dealCards(room);
        dealt.forEach((pid, cards) -> assertThat(cards).hasSize(13));
    }

    // -----------------------------------------------------------------------
    // drawFromDeck
    // -----------------------------------------------------------------------

    @Test @DisplayName("drawFromDeck adds card to player's hand")
    void drawFromDeckAddsCard() {
        GameRoom room = startedRoom();
        Player current = room.currentPlayer().orElseThrow();
        int before = current.handSize();

        engine.drawFromDeck(room, current.getPlayerId());

        assertThat(current.handSize()).isEqualTo(before + 1);
        assertThat(room.isCurrentPlayerHasDrawn()).isTrue();
    }

    @Test @DisplayName("drawFromDeck throws when not player's turn")
    void drawFromDeckWrongTurn() {
        GameRoom room = startedRoom();
        Player second = room.getPlayers().get(1);
        assertThatThrownBy(() -> engine.drawFromDeck(room, second.getPlayerId()))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("not your turn");
    }

    @Test @DisplayName("drawFromDeck throws if player already drew this turn")
    void drawFromDeckTwice() {
        GameRoom room = startedRoom();
        Player current = room.currentPlayer().orElseThrow();
        engine.drawFromDeck(room, current.getPlayerId());
        assertThatThrownBy(() -> engine.drawFromDeck(room, current.getPlayerId()))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("already drawn");
    }

    // -----------------------------------------------------------------------
    // discardCard
    // -----------------------------------------------------------------------

    @Test @DisplayName("discardCard removes card and advances turn")
    void discardCardAdvancesTurn() {
        GameRoom room = startedRoom();
        Player current = room.currentPlayer().orElseThrow();
        engine.drawFromDeck(room, current.getPlayerId());

        Card toDiscard = current.getHandCards().get(0);
        int handBefore = current.handSize();

        engine.discardCard(room, current.getPlayerId(), toDiscard.cardId());

        assertThat(current.handSize()).isEqualTo(handBefore - 1);
        assertThat(room.peekTopDiscard()).hasValue(toDiscard);
        // Turn should have advanced
        assertThat(room.currentPlayer().orElseThrow().getPlayerId())
                .isNotEqualTo(current.getPlayerId());
    }

    @Test @DisplayName("discardCard throws if card not in hand")
    void discardNotInHand() {
        GameRoom room = startedRoom();
        Player current = room.currentPlayer().orElseThrow();
        engine.drawFromDeck(room, current.getPlayerId());
        assertThatThrownBy(() -> engine.discardCard(room, current.getPlayerId(), "FAKE-ID"))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("not found");
    }

    // -----------------------------------------------------------------------
    // declareWin
    // -----------------------------------------------------------------------

    @Test @DisplayName("declareWin succeeds with valid 4+3+3+3 grouping (no Joker needed)")
    void declareWinValid() {
        GameRoom room = startedRoom();
        Player winner = room.getPlayers().get(0);

        // Replace player's hand with a known winning arrangement
        winner.getHandCards().clear();
        winner.getHandCards().addAll(buildWinningHand());

        List<List<String>> groups = buildWinningGroups(winner.getHandCards());

        room.setCurrentPlayerHasDrawn(true); // pretend they already drew
        // Actually declareWin works from a 13-card hand too - set drawn=true and remove to 13
        // For this test, hand is exactly 13 (no 14th card)
        boolean result = engine.declareWin(room, winner.getPlayerId(), groups, null);

        assertThat(result).isTrue();
        assertThat(room.getStatus()).isEqualTo(RoomStatus.FINISHED);
        assertThat(room.getWinnerId()).isEqualTo(winner.getPlayerId());
        assertThat(room.isJokerUnlocked()).isTrue();
    }

    @Test @DisplayName("declareWin fails when 4-card group has mixed ranks")
    void declareWinInvalid4CardGroup() {
        GameRoom room = startedRoom();
        Player p = room.getPlayers().get(0);

        p.getHandCards().clear();
        // Create invalid 4-card group with mixed ranks
        List<Card> invalidHand = new ArrayList<>();
        invalidHand.add(new Card("H-5-1", Suit.HEARTS,   Rank.FIVE,  false));
        invalidHand.add(new Card("D-5-2", Suit.DIAMONDS, Rank.FIVE,  false));
        invalidHand.add(new Card("C-5-1", Suit.CLUBS,    Rank.FIVE,  false));
        invalidHand.add(new Card("S-6-1", Suit.SPADES,   Rank.SIX,   false)); // Mixed rank!
        // fill rest with 3 groups of 3
        for (int i = 0; i < 9; i++) {
            invalidHand.add(new Card("H-7-" + i, Suit.HEARTS, Rank.SEVEN, false));
        }
        p.getHandCards().addAll(invalidHand.subList(0, 13));

        List<List<String>> groups = List.of(
                List.of("H-5-1","D-5-2","C-5-1","S-6-1"),
                List.of("H-7-0","H-7-1","H-7-2"),
                List.of("H-7-3","H-7-4","H-7-5"),
                List.of("H-7-6","H-7-7","H-7-8")
        );

        assertThatThrownBy(() -> engine.declareWin(room, p.getPlayerId(), groups, null))
                .isInstanceOf(InvalidMoveException.class);
    }

    @Test @DisplayName("declareWin fails with wrong number of groups")
    void declareWinWrongGroupCount() {
        GameRoom room = startedRoom();
        Player p = room.getPlayers().get(0);
        p.getHandCards().clear();
        p.getHandCards().addAll(buildWinningHand());

        List<List<String>> threeGroups = buildWinningGroups(p.getHandCards()).subList(0, 3);

        assertThatThrownBy(() -> engine.declareWin(room, p.getPlayerId(), threeGroups, null))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("4 groups");
    }

    @Test @DisplayName("isPureSet returns true for same-rank cards")
    void isPureSetTrue() {
        List<Card> cards = List.of(
                new Card("H-5-1", Suit.HEARTS,   Rank.FIVE, false),
                new Card("D-5-2", Suit.DIAMONDS, Rank.FIVE, false),
                new Card("C-5-1", Suit.CLUBS,    Rank.FIVE, false)
        );
        assertThat(engine.isPureSet(cards)).isTrue();
    }

    @Test @DisplayName("isPureSet returns false for mixed ranks")
    void isPureSetFalse() {
        List<Card> cards = List.of(
                new Card("H-5-1", Suit.HEARTS,   Rank.FIVE, false),
                new Card("D-6-2", Suit.DIAMONDS, Rank.SIX,  false)
        );
        assertThat(engine.isPureSet(cards)).isFalse();
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private GameRoom make4PlayerRoom() {
        GameRoom room = new GameRoom("test-id", "TESTA1");
        for (int i = 0; i < 4; i++) {
            room.getPlayers().add(new Player("p" + i, "Player" + i, "s" + i, i));
        }
        return room;
    }

    private GameRoom startedRoom() {
        GameRoom room = make4PlayerRoom();
        room.setStatus(RoomStatus.PLAYING);
        engine.dealCards(room);
        return room;
    }

    /**
     * Builds a hand of 13 cards that forms a valid winning arrangement:
     * Group of 4 (ACEs) + 3 groups of 3 (5s, 7s, Ks).
     */
    private List<Card> buildWinningHand() {
        return new ArrayList<>(List.of(
                new Card("H-A-1", Suit.HEARTS,   Rank.ACE,  false),
                new Card("D-A-2", Suit.DIAMONDS, Rank.ACE,  false),
                new Card("C-A-1", Suit.CLUBS,    Rank.ACE,  false),
                new Card("S-A-2", Suit.SPADES,   Rank.ACE,  false),
                new Card("H-5-1", Suit.HEARTS,   Rank.FIVE, false),
                new Card("D-5-2", Suit.DIAMONDS, Rank.FIVE, false),
                new Card("C-5-1", Suit.CLUBS,    Rank.FIVE, false),
                new Card("H-7-1", Suit.HEARTS,   Rank.SEVEN,false),
                new Card("D-7-2", Suit.DIAMONDS, Rank.SEVEN,false),
                new Card("C-7-1", Suit.CLUBS,    Rank.SEVEN,false),
                new Card("H-K-1", Suit.HEARTS,   Rank.KING, false),
                new Card("D-K-2", Suit.DIAMONDS, Rank.KING, false),
                new Card("C-K-1", Suit.CLUBS,    Rank.KING, false)
        ));
    }

    private List<List<String>> buildWinningGroups(List<Card> hand) {
        // ACEs (4) + FIVEs (3) + SEVENs (3) + KINGs (3)
        return List.of(
                hand.stream().filter(c -> c.rank() == Rank.ACE)  .map(Card::cardId).toList(),
                hand.stream().filter(c -> c.rank() == Rank.FIVE) .map(Card::cardId).toList(),
                hand.stream().filter(c -> c.rank() == Rank.SEVEN).map(Card::cardId).toList(),
                hand.stream().filter(c -> c.rank() == Rank.KING) .map(Card::cardId).toList()
        );
    }
}


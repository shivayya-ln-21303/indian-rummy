package com.game.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Deck}.
 */
class DeckTest {

    private Deck deck;

    @BeforeEach
    void setUp() {
        deck = new Deck();
    }

    @Test
    @DisplayName("New deck contains exactly 52 cards")
    void newDeckHas52Cards() {
        assertThat(deck.size()).isEqualTo(52);
        assertThat(deck.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("All 52 cards are unique")
    void allCardsAreUnique() {
        List<Card> all = new ArrayList<>();
        while (!deck.isEmpty()) {
            deck.draw().ifPresent(all::add);
        }

        Set<String> unique = new HashSet<>();
        for (Card c : all) {
            unique.add(c.suit() + "-" + c.rank());
        }
        assertThat(unique).hasSize(52);
    }

    @Test
    @DisplayName("draw() reduces size by 1 each call")
    void drawReducesSize() {
        int initial = deck.size();
        deck.draw();
        assertThat(deck.size()).isEqualTo(initial - 1);
    }

    @Test
    @DisplayName("draw() returns empty when deck is exhausted")
    void drawReturnsEmptyWhenExhausted() {
        // Drain entire deck
        while (!deck.isEmpty()) {
            deck.draw();
        }
        Optional<Card> result = deck.draw();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("shuffle() does not change the number of cards")
    void shufflePreservesCount() {
        deck.shuffle();
        assertThat(deck.size()).isEqualTo(52);
    }

    @Test
    @DisplayName("shuffle() produces a different order (statistically)")
    void shuffleChangesOrder() {
        List<Card> before = new ArrayList<>(deck.peekAll());
        deck.shuffle();
        List<Card> after = deck.peekAll();

        // With 52! possible orderings the chance of same order is negligible
        assertThat(before).isNotEqualTo(after);
    }

    @Test
    @DisplayName("addCards() increases the deck size correctly")
    void addCardsIncreasesDeckSize() {
        List<Card> extras = List.of(
                new Card(Suit.HEARTS, Rank.ACE),
                new Card(Suit.SPADES, Rank.KING)
        );
        int before = deck.size();
        deck.addCards(extras);
        assertThat(deck.size()).isEqualTo(before + 2);
    }

    @Test
    @DisplayName("Deck constructed from a list contains exactly those cards")
    void deckFromList() {
        List<Card> cards = List.of(
                new Card(Suit.HEARTS,   Rank.ACE),
                new Card(Suit.DIAMONDS, Rank.TWO)
        );
        Deck custom = new Deck(cards);
        assertThat(custom.size()).isEqualTo(2);
    }
}


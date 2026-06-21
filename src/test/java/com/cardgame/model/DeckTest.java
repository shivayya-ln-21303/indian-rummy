package com.cardgame.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class DeckTest {

    private Deck deck;

    @BeforeEach void setup() { deck = new Deck(); }

    @Test @DisplayName("Full deck has 105 cards (2×52 + 1 Joker)")
    void fullDeckSize() {
        assertThat(deck.size()).isEqualTo(105);
    }

    @Test @DisplayName("Exactly 1 Joker in deck")
    void oneJoker() {
        long jokers = deck.peekAll().stream().filter(Card::isJoker).count();
        assertThat(jokers).isEqualTo(1);
    }

    @Test @DisplayName("Joker card ID is 'JKR'")
    void jokerCardId() {
        Optional<Card> joker = deck.peekAll().stream().filter(Card::isJoker).findFirst();
        assertThat(joker).isPresent();
        assertThat(joker.get().cardId()).isEqualTo("JKR");
    }

    @Test @DisplayName("Each non-joker card appears exactly twice (two decks)")
    void eachCardTwice() {
        Map<String, Integer> counts = new HashMap<>();
        for (Card c : deck.peekAll()) {
            if (!c.isJoker()) {
                counts.merge(c.suit() + "-" + c.rank(), 1, Integer::sum);
            }
        }
        assertThat(counts.values()).allMatch(v -> v == 2);
        assertThat(counts).hasSize(52); // 4 suits × 13 ranks
    }

    @Test @DisplayName("shuffle() preserves card count")
    void shufflePreservesCount() {
        deck.shuffle();
        assertThat(deck.size()).isEqualTo(105);
    }

    @Test @DisplayName("draw() removes one card per call")
    void drawReducesSize() {
        deck.draw();
        assertThat(deck.size()).isEqualTo(104);
    }

    @Test @DisplayName("draw() returns empty when exhausted")
    void drawEmptyDeck() {
        while (!deck.isEmpty()) deck.draw();
        assertThat(deck.draw()).isEmpty();
    }

    @Test @DisplayName("4 players × 13 cards = 52 drawn, 53 remain")
    void dealScenario() {
        for (int i = 0; i < 52; i++) deck.draw();
        assertThat(deck.size()).isEqualTo(53);
    }
}


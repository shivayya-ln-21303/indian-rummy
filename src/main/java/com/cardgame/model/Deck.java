package com.cardgame.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Indian Rummy deck: 2 × 52 regular cards + 1 printed Joker = 105 cards total.
 *
 * <p>Not thread-safe on its own — callers must hold the room lock.</p>
 */
public class Deck {

    private final List<Card> cards = new ArrayList<>(105);

    public Deck() {
        build();
    }

    // ---------------------------------------------------------------------------
    // Build
    // ---------------------------------------------------------------------------

    private void build() {
        cards.clear();
        for (int deckNum = 1; deckNum <= 2; deckNum++) {
            for (Suit suit : Suit.values()) {
                if (suit == Suit.JOKER) continue;
                for (Rank rank : Rank.values()) {
                    if (rank == Rank.JOKER) continue;
                    // cardId format: "H-A-1", "D-5-2", "S-K-1" etc.
                    String suitChar = String.valueOf(suit.name().charAt(0));
                    String cardId   = suitChar + "-" + rank.getDisplayName() + "-" + deckNum;
                    cards.add(new Card(cardId, suit, rank, false));
                }
            }
        }
        // One printed Joker
        cards.add(new Card("JKR", Suit.JOKER, Rank.JOKER, true));
    }

    // ---------------------------------------------------------------------------
    // Operations
    // ---------------------------------------------------------------------------

    public void shuffle() {
        Collections.shuffle(cards);
    }

    /**
     * Draws (removes) the top card from the deck.
     *
     * @return the drawn card, or empty if deck is exhausted.
     */
    public Optional<Card> draw() {
        if (cards.isEmpty()) return Optional.empty();
        return Optional.of(cards.remove(cards.size() - 1));
    }

    public boolean isEmpty() { return cards.isEmpty(); }
    public int    size()     { return cards.size(); }

    /** Peek at the top card without removing it. */
    public Optional<Card> peek() {
        if (cards.isEmpty()) return Optional.empty();
        return Optional.of(cards.get(cards.size() - 1));
    }

    public List<Card> peekAll() {
        return Collections.unmodifiableList(cards);
    }
}


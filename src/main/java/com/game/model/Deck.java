package com.game.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A standard 52-card deck.
 * <p>
 * Not thread-safe on its own — callers must hold the room lock before
 * calling any mutating method ({@code shuffle}, {@code draw}, {@code addCards}).
 * </p>
 */
public class Deck {

    private final List<Card> cards = new ArrayList<>(52);

    /** Creates and populates a full 52-card deck (not yet shuffled). */
    public Deck() {
        initialize();
    }

    /** Re-populates the deck from an external list (used when reshuffling the discard pile). */
    public Deck(List<Card> initialCards) {
        cards.addAll(initialCards);
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private void initialize() {
        cards.clear();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Shuffles the deck using a Fisher-Yates shuffle via {@link Collections#shuffle}.
     */
    public void shuffle() {
        Collections.shuffle(cards);
    }

    /**
     * Draws (removes) the top card from the deck.
     *
     * @return the top card, or {@link Optional#empty()} if the deck is exhausted.
     */
    public Optional<Card> draw() {
        if (cards.isEmpty()) {
            return Optional.empty();
        }
        // Remove from the end of the list — O(1)
        return Optional.of(cards.remove(cards.size() - 1));
    }

    /**
     * Adds a collection of cards to the bottom of the deck.
     * Used when reshuffling the discard pile back into the draw pile.
     */
    public void addCards(List<Card> newCards) {
        cards.addAll(0, newCards);
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public int size() {
        return cards.size();
    }

    /** Returns a read-only snapshot of the remaining cards (for debugging / tests). */
    public List<Card> peekAll() {
        return Collections.unmodifiableList(cards);
    }
}


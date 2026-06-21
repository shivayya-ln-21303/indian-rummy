package com.game.model;

/**
 * Represents a single playing card.
 * Declared as a Java record making it immutable by design —
 * cards never change once dealt from the deck.
 */
public record Card(Suit suit, Rank rank) {

    /**
     * Human-readable label, e.g. "Ace of Spades".
     */
    public String getDisplayName() {
        return rank.getDisplayName() + " of " + suit.getDisplayName();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}


package com.cardgame.model;

/**
 * Immutable playing card.
 *
 * <p>The {@code cardId} uniquely identifies the physical card across both decks,
 * using the pattern: {@code SUIT_INITIAL-RANK_DISPLAY-DECK_NUM}
 * <br>Examples: {@code "H-A-1"} (Ace of Hearts, deck 1), {@code "D-5-2"}
 * (5 of Diamonds, deck 2), {@code "JKR"} (printed Joker).
 * </p>
 */
public record Card(String cardId, Suit suit, Rank rank, boolean joker) {

    /** Returns {@code true} if this card is the printed Joker wildcard. */
    public boolean isJoker() {
        return joker;
    }

    public String getDisplayName() {
        if (joker) return "Joker";
        return rank.getDisplayName() + suit.getSymbol();
    }

    @Override
    public String toString() {
        return cardId + "(" + getDisplayName() + ")";
    }
}


package com.cardgame.model;

/** The four standard suits plus the special Joker pseudo-suit. */
public enum Suit {
    HEARTS("Hearts", "♥", true),
    DIAMONDS("Diamonds", "♦", true),
    CLUBS("Clubs", "♣", false),
    SPADES("Spades", "♠", false),
    JOKER("Joker", "🃏", false);

    private final String displayName;
    private final String symbol;
    private final boolean red;

    Suit(String displayName, String symbol, boolean red) {
        this.displayName = displayName;
        this.symbol      = symbol;
        this.red         = red;
    }

    public String getDisplayName() { return displayName; }
    public String getSymbol()      { return symbol; }
    public boolean isRed()         { return red; }
}


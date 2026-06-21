package com.cardgame.model;

/** Life-cycle states of a {@link GameRoom}. */
public enum RoomStatus {
    /** Waiting for all 4 players to join. */
    WAITING_FOR_PLAYERS,

    /** Cards are being distributed (brief transitional state). */
    DEALING,

    /** Game is in progress, Joker still hidden. */
    PLAYING,

    /** A valid 4-card set has been formed — Joker is now active and visible. */
    JOKER_UNLOCKED,

    /** A player has declared a valid win. */
    FINISHED
}


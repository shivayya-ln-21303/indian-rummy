package com.game.model;

/** Possible life-cycle states of a {@link GameRoom}. */
public enum RoomState {

    /** Waiting for players to join; game has not started. */
    WAITING,

    /** Game is in progress. */
    STARTED,

    /** Game has ended; a winner has been determined. */
    FINISHED
}


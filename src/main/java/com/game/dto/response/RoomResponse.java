package com.game.dto.response;

import com.game.model.RoomState;

import java.util.List;

/**
 * Summary of a game room — safe to return over REST or WebSocket to any player.
 */
public record RoomResponse(
        String roomId,
        RoomState roomState,
        List<PlayerInfo> players,
        int maxPlayers,
        boolean canStart
) {}


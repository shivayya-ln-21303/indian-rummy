package com.cardgame.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Generic outbound WebSocket envelope.
 * <pre>
 * { "type": "CARD_DRAWN", "success": true, "data": {...}, "timestamp": "..." }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebSocketResponse(
        String  type,
        boolean success,
        Object  data,
        String  error,
        Instant timestamp
) {
    public static WebSocketResponse ok(String type, Object data) {
        return new WebSocketResponse(type, true, data, null, Instant.now());
    }

    public static WebSocketResponse err(String type, String errorMessage) {
        return new WebSocketResponse(type, false, null, errorMessage, Instant.now());
    }
}


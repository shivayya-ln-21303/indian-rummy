package com.game.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Generic outbound WebSocket envelope.
 *
 * <pre>
 * {
 *   "type"      : "CARD_PLAYED",
 *   "success"   : true,
 *   "data"      : { ... },
 *   "error"     : null,
 *   "timestamp" : "2024-01-01T12:00:00Z"
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebSocketResponse(
        String type,
        boolean success,
        Object data,
        String error,
        Instant timestamp
) {

    // ---------------------------------------------------------------------------
    // Factory methods
    // ---------------------------------------------------------------------------

    public static WebSocketResponse success(String type, Object data) {
        return new WebSocketResponse(type, true, data, null, Instant.now());
    }

    public static WebSocketResponse error(String type, String errorMessage) {
        return new WebSocketResponse(type, false, null, errorMessage, Instant.now());
    }
}


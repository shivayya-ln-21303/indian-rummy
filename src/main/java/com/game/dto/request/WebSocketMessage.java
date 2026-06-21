package com.game.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Generic inbound WebSocket envelope.
 *
 * <pre>
 * {
 *   "type": "PLAY_CARD",
 *   "payload": { "cardIndex": 2 }
 * }
 * </pre>
 *
 * The {@code payload} is kept as a raw {@link JsonNode} so that each
 * handler can deserialize it into the exact DTO it expects.
 */
public record WebSocketMessage(String type, JsonNode payload) {

    @JsonCreator
    public WebSocketMessage(
            @JsonProperty("type") String type,
            @JsonProperty("payload") JsonNode payload) {
        this.type    = type;
        this.payload = payload;
    }
}


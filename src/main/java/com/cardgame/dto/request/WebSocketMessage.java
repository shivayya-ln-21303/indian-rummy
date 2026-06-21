package com.cardgame.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Generic inbound WebSocket envelope.
 * <pre>{ "type": "DRAW_CARD", "payload": { ... } }</pre>
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


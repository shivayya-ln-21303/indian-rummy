package com.game.config;

import com.game.websocket.GameWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the raw WebSocket endpoint.
 *
 * <p>We use raw WebSocket (not STOMP/SockJS) for maximum control and
 * minimum overhead — the game engine is fully authoritative and does not
 * need a message-broker abstraction.</p>
 *
 * <p>The endpoint is {@code ws://host:port/ws/game}.
 * CORS is opened to all origins here for development; restrict in production.</p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;

    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler) {
        this.gameWebSocketHandler = gameWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/ws/game")
                .setAllowedOrigins("*");   // TODO: restrict to known origins in production
    }
}


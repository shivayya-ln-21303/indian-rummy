package com.cardgame.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of all active WebSocket sessions.
 * Sends are synchronised on the individual session object as required by the WS spec.
 */
@Component
public class WebSocketSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>(4096);

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public Optional<WebSocketSession> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public int count() { return sessions.size(); }

    /** Sends JSON to a single session. Silently ignores closed sessions. */
    public void send(String sessionId, String json) {
        WebSocketSession s = sessions.get(sessionId);
        if (s == null || !s.isOpen()) return;
        synchronized (s) {
            if (!s.isOpen()) return;
            try {
                s.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.warn("Send failed to session {}: {}", sessionId, e.getMessage());
            }
        }
    }

    /** Broadcasts the same JSON to multiple sessions. */
    public void broadcast(Collection<String> sessionIds, String json) {
        sessionIds.forEach(id -> send(id, json));
    }
}


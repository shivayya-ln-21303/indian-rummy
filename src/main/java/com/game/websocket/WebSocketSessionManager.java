package com.game.websocket;

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
 * Centralised registry of live WebSocket sessions.
 *
 * <p>Thread-safe: all operations use a {@link ConcurrentHashMap}.</p>
 *
 * <p>Sending is synchronised on the individual session to comply with the
 * WebSocket spec which forbids concurrent writes to the same session.</p>
 */
@Component
public class WebSocketSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);

    /** sessionId → live WebSocket session */
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>(2048);

    // ---------------------------------------------------------------------------
    // Session lifecycle
    // ---------------------------------------------------------------------------

    public void registerSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.debug("Session registered: {}", session.getId());
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        log.debug("Session removed: {}", sessionId);
    }

    public Optional<WebSocketSession> getSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public Collection<WebSocketSession> getAllSessions() {
        return sessions.values();
    }

    public int activeSessionCount() {
        return sessions.size();
    }

    // ---------------------------------------------------------------------------
    // Send helpers
    // ---------------------------------------------------------------------------

    /**
     * Sends a JSON string to a specific session.
     * Silently ignores the send if the session is closed.
     */
    public void sendToSession(String sessionId, String json) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            log.debug("Cannot send to session [{}] — not found or closed", sessionId);
            return;
        }
        sendSafe(session, json);
    }

    /**
     * Broadcasts a JSON string to every session in the given collection of session IDs.
     * Errors on individual sessions do not affect other deliveries.
     */
    public void broadcast(Collection<String> sessionIds, String json) {
        for (String id : sessionIds) {
            sendToSession(id, json);
        }
    }

    // ---------------------------------------------------------------------------
    // Private
    // ---------------------------------------------------------------------------

    /**
     * Thread-safe send: synchronises on the session object as required by the
     * Spring WebSocket API to prevent concurrent writes.
     */
    private void sendSafe(WebSocketSession session, String json) {
        synchronized (session) {
            if (!session.isOpen()) return;
            try {
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.warn("Failed to send message to session [{}]: {}", session.getId(), e.getMessage());
            }
        }
    }
}


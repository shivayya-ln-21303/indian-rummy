package com.cardgame.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

/**
 * Manages per-room turn timers.
 * When a player's 30-second turn timer expires, the provided {@code Runnable} is executed.
 * The timer is cancelled when the player takes an action before it fires.
 */
@Service
public class TurnTimerService {

    private static final Logger log = LoggerFactory.getLogger(TurnTimerService.class);

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4, r -> {
                Thread t = new Thread(r, "turn-timer");
                t.setDaemon(true);
                return t;
            });

    /** roomId → current pending timer */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    /**
     * Starts (or restarts) the turn timer for a room.
     *
     * @param roomId   Room identifier.
     * @param seconds  Timeout duration.
     * @param onExpiry Callback executed on the timer thread when the timer fires.
     *                 The callback must be thread-safe and short-lived.
     */
    public void start(String roomId, int seconds, Runnable onExpiry) {
        cancel(roomId); // Cancel any existing timer first
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                onExpiry.run();
            } catch (Exception e) {
                log.error("Error in turn timer callback for room [{}]", roomId, e);
            } finally {
                timers.remove(roomId);
            }
        }, seconds, TimeUnit.SECONDS);
        timers.put(roomId, future);
        log.debug("Turn timer started for room [{}] — {} seconds", roomId, seconds);
    }

    /** Cancels the pending timer for a room (called when player acts). */
    public void cancel(String roomId) {
        ScheduledFuture<?> f = timers.remove(roomId);
        if (f != null) {
            f.cancel(false);
            log.debug("Turn timer cancelled for room [{}]", roomId);
        }
    }

    /** Returns approximate remaining seconds for a room's timer (for UI countdown). */
    public long remainingSeconds(String roomId) {
        ScheduledFuture<?> f = timers.get(roomId);
        if (f == null) return 0;
        return f.getDelay(TimeUnit.SECONDS);
    }
}


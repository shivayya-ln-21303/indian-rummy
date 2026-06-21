package com.game.service;

import com.game.manager.RoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Background service that periodically removes stale game rooms and
 * disconnected players to keep the in-memory footprint bounded.
 *
 * <p>Runs on a {@code ScheduledExecutorService} thread pool managed by Spring's
 * {@code @EnableScheduling} infrastructure (configured in {@code AppConfig}).</p>
 */
@Service
public class CleanupService {

    private static final Logger log = LoggerFactory.getLogger(CleanupService.class);

    private final RoomManager roomManager;

    public CleanupService(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    /**
     * Runs every 5 minutes (configurable via {@code game.cleanup-interval-minutes}).
     * Removes any room whose {@code lastActivityTime} is older than
     * {@code game.room-inactive-minutes} minutes.
     */
    @Scheduled(fixedRateString = "${game.cleanup-interval-minutes:5}000",   // default 5 000 ms → 5 s for demo
            initialDelayString = "60000")                                    // wait 1 min before first run
    public void cleanupInactiveRooms() {
        int before = roomManager.activeRoomCount();
        log.debug("Cleanup scheduler running — active rooms before: {}", before);

        try {
            roomManager.removeInactiveRooms();
        } catch (Exception ex) {
            // Never let the scheduler thread die
            log.error("Error during room cleanup", ex);
        }

        int after = roomManager.activeRoomCount();
        if (before != after) {
            log.info("Cleanup complete — removed {} stale room(s), {} remain", before - after, after);
        } else {
            log.debug("Cleanup complete — no stale rooms found");
        }
    }
}


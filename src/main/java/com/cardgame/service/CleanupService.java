package com.cardgame.service;

import com.cardgame.manager.RoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodically evicts stale / empty game rooms to keep RAM usage bounded.
 */
@Service
public class CleanupService {

    private static final Logger log = LoggerFactory.getLogger(CleanupService.class);

    private final RoomManager roomManager;

    public CleanupService(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    /** Runs every 5 minutes. Removes rooms inactive for ≥ 30 minutes. */
    @Scheduled(fixedRateString = "${game.cleanup-interval-ms:300000}", initialDelayString = "60000")
    public void cleanup() {
        int before = roomManager.activeRoomCount();
        try {
            roomManager.removeInactiveRooms();
        } catch (Exception e) {
            log.error("Cleanup error", e);
        }
        int after = roomManager.activeRoomCount();
        if (before != after) {
            log.info("Cleanup: removed {} room(s), {} active", before - after, after);
        }
    }
}


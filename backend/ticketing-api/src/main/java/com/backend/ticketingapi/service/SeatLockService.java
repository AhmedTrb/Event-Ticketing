package com.backend.ticketingapi.service;

import com.backend.shared.exceptions.SeatAlreadyBookedException;
import com.backend.shared.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatLockService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    // Lock duration in seconds (e.g., 10 minutes)
    private static final long LOCK_DURATION_SECONDS = 600;

    /**
     * Locks a list of seats for a specific user and event.
     * Uses Redis SET NX (Set if Not Exists) to ensure atomicity.
     *
     * @param eventId         The event ID
     * @param seatIdentifiers List of seat IDs/labels
     * @param userId          The user ID
     */
    public void lockSeats(UUID eventId, List<String> seatIdentifiers, UUID userId) {
        for (String seatId : seatIdentifiers) {
            String lockKey = generateLockKey(eventId, seatId);
            String lockValue = "locked_by_" + userId.toString();

            // Try to set the lock in Redis with expiration
            Boolean success = redisTemplate.opsForValue().setIfAbsent(
                    lockKey,
                    lockValue,
                    Duration.ofSeconds(LOCK_DURATION_SECONDS));

            if (Boolean.FALSE.equals(success)) {
                // Check who holds the lock
                String currentOwner = (String) redisTemplate.opsForValue().get(lockKey);
                log.warn("Seat {} is already locked by {}", seatId, currentOwner);

                // Rollback attempt (simple)
                unlockSeats(eventId, seatIdentifiers, userId);
                throw new SeatAlreadyBookedException(eventId, seatId);
            }

            // Broadcast lock event to WebSocket topic
            // Topic: /topic/events/{eventId}/seats
            messagingTemplate.convertAndSend("/topic/events/" + eventId + "/seats",
                    Map.of("seatId", seatId, "status", "LOCKED", "userId", userId));
        }
        log.info("Successfully locked {} seats for user {} on event {}", seatIdentifiers.size(), userId, eventId);
    }

    /**
     * Unlocks seats.
     */
    public void unlockSeats(UUID eventId, List<String> seatIdentifiers, UUID userId) {
        for (String seatId : seatIdentifiers) {
            String lockKey = generateLockKey(eventId, seatId);
            // In a real production system, use a Lua script to check if the value matches
            // userId before deleting
            redisTemplate.delete(lockKey);

            // Broadcast unlock event
            messagingTemplate.convertAndSend("/topic/events/" + eventId + "/seats",
                    Map.of("seatId", seatId, "status", "AVAILABLE"));
        }
        log.info("Unlocked seats {} for event {}", seatIdentifiers, eventId);
    }

    public boolean isSeatLocked(UUID eventId, String seatId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(generateLockKey(eventId, seatId)));
    }

    private String generateLockKey(UUID eventId, String seatId) {
        return "seat_lock:" + eventId + ":" + seatId;
    }
}

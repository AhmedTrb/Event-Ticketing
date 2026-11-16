package com.backend.registrationworker.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Publishes booking outcome notifications as JSON strings on the Redis pub/sub
 * channel consumed by notification-service (which pushes them over WebSocket).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationPublisher {

    /** Must match notification-service's subscribed channel. */
    public static final String REDIS_CHANNEL = "notifications";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void publishBookingConfirmation(UUID userId, UUID bookingId, UUID eventId) {
        publishEvent("BOOKING_CONFIRMED", userId, bookingId, eventId, "Your booking has been confirmed!");
    }

    public void publishBookingFailure(UUID userId, UUID bookingId, UUID eventId, String reason) {
        publishEvent("BOOKING_FAILED", userId, bookingId, eventId, "Booking failed: " + reason);
    }

    private void publishEvent(String type, UUID userId, UUID bookingId, UUID eventId, String message) {
        Map<String, Object> event = Map.of(
                "type", type,
                "userId", userId.toString(),
                "bookingId", bookingId.toString(),
                "eventId", eventId.toString(),
                "message", message,
                "timestamp", System.currentTimeMillis());
        try {
            redisTemplate.convertAndSend(REDIS_CHANNEL, objectMapper.writeValueAsString(event));
            log.info("Published notification event: {}", event);
        } catch (JsonProcessingException e) {
            log.error("Could not serialise notification {}", event, e);
        }
    }
}

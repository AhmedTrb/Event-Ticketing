package com.backend.registrationworker.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationPublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    // Channel for real-time notification
    private static final String redisChannel = "notification";

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

        redisTemplate.convertAndSend(redisChannel, event);
        log.info("Published notification event: {}", event);
    }
}

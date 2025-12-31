package com.backend.shared.messaging.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a new booking is created.
 * This event is consumed by the registration worker for processing.
 */
@Getter
@ToString
@EqualsAndHashCode
public class BookingCreatedEvent {

    /**
     * Unique booking ID
     */
    private final UUID bookingId;

    /**
     * User who created the booking
     */
    private final UUID userId;

    /**
     * Event being booked
     */
    private final UUID eventId;

    /**
     * Number of seats requested
     */
    private final int seatCount;

    /**
     * When the booking was created
     */
    private final Instant createdAt;

    @JsonCreator
    public BookingCreatedEvent(
            @JsonProperty("bookingId") UUID bookingId,
            @JsonProperty("userId") UUID userId,
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("seatCount") int seatCount,
            @JsonProperty("createdAt") Instant createdAt) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.eventId = eventId;
        this.seatCount = seatCount;
        this.createdAt = createdAt;
    }
}

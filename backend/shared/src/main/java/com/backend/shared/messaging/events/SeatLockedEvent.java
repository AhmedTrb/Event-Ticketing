package com.backend.shared.messaging.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event published when seats are locked for a booking session.
 * Notifies other services about seat locks and expiration times.
 */
@Getter
@ToString
@EqualsAndHashCode
public class SeatLockedEvent {

    /**
     * The booking ID associated with this lock
     */
    private final UUID bookingId;

    /**
     * User who locked the seats
     */
    private final UUID userId;

    /**
     * Event ID
     */
    private final UUID eventId;

    /**
     * List of seat identifiers that were locked
     */
    private final List<String> seatIdentifiers;

    /**
     * When the lock was created
     */
    private final Instant lockedAt;

    /**
     * When the lock will expire
     */
    private final Instant expiresAt;

    @JsonCreator
    public SeatLockedEvent(
            @JsonProperty("bookingId") UUID bookingId,
            @JsonProperty("userId") UUID userId,
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("seatIdentifiers") List<String> seatIdentifiers,
            @JsonProperty("lockedAt") Instant lockedAt,
            @JsonProperty("expiresAt") Instant expiresAt) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.eventId = eventId;
        this.seatIdentifiers = seatIdentifiers;
        this.lockedAt = lockedAt;
        this.expiresAt = expiresAt;
    }
}

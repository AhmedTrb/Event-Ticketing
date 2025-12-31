package com.backend.shared.messaging.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.UUID;

/**
 * Command to process a booking request asynchronously.
 * Sent to the registration worker queue for processing.
 */
@Getter
@ToString
@EqualsAndHashCode
public class ProcessBookingCommand {

    /**
     * Booking ID to process
     */
    private final UUID bookingId;

    /**
     * User making the booking
     */
    private final UUID userId;

    /**
     * Event to book
     */
    private final UUID eventId;

    /**
     * Selected seat identifiers
     */
    private final List<String> selectedSeats;

    /**
     * Whether this booking is from a queued request
     */
    private final boolean fromQueue;

    @JsonCreator
    public ProcessBookingCommand(
            @JsonProperty("bookingId") UUID bookingId,
            @JsonProperty("userId") UUID userId,
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("selectedSeats") List<String> selectedSeats,
            @JsonProperty("fromQueue") boolean fromQueue) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.eventId = eventId;
        this.selectedSeats = selectedSeats;
        this.fromQueue = fromQueue;
    }
}

package com.backend.shared.messaging.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a ticket is issued to a user.
 * Triggers email notification with PDF ticket.
 */
@Getter
@ToString
@EqualsAndHashCode
public class TicketIssuedEvent {

    /**
     * Unique ticket ID
     */
    private final UUID ticketId;

    /**
     * Associated booking ID
     */
    private final UUID bookingId;

    /**
     * User who received the ticket
     */
    private final UUID userId;

    /**
     * Event ID
     */
    private final UUID eventId;

    /**
     * Seat identifier
     */
    private final String seatIdentifier;

    /**
     * QR code for ticket validation
     */
    private final String qrCode;

    /**
     * When the ticket was issued
     */
    private final Instant issuedAt;

    @JsonCreator
    public TicketIssuedEvent(
            @JsonProperty("ticketId") UUID ticketId,
            @JsonProperty("bookingId") UUID bookingId,
            @JsonProperty("userId") UUID userId,
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("seatIdentifier") String seatIdentifier,
            @JsonProperty("qrCode") String qrCode,
            @JsonProperty("issuedAt") Instant issuedAt) {
        this.ticketId = ticketId;
        this.bookingId = bookingId;
        this.userId = userId;
        this.eventId = eventId;
        this.seatIdentifier = seatIdentifier;
        this.qrCode = qrCode;
        this.issuedAt = issuedAt;
    }
}

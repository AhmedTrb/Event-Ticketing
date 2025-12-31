package com.backend.shared.messaging.events;

import com.backend.shared.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a payment is completed successfully.
 * Triggers ticket generation and confirmation emails.
 */
@Getter
@ToString
@EqualsAndHashCode
public class PaymentCompletedEvent {

    /**
     * Unique payment ID
     */
    private final UUID paymentId;

    /**
     * Associated booking ID
     */
    private final UUID bookingId;

    /**
     * User who made the payment
     */
    private final UUID userId;

    /**
     * Event ID
     */
    private final UUID eventId;

    /**
     * Payment amount
     */
    private final BigDecimal amount;

    /**
     * Payment status
     */
    private final PaymentStatus status;

    /**
     * When the payment was completed
     */
    private final Instant completedAt;

    /**
     * External payment reference (e.g., Stripe payment intent ID)
     */
    private final String paymentReference;

    @JsonCreator
    public PaymentCompletedEvent(
            @JsonProperty("paymentId") UUID paymentId,
            @JsonProperty("bookingId") UUID bookingId,
            @JsonProperty("userId") UUID userId,
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("status") PaymentStatus status,
            @JsonProperty("completedAt") Instant completedAt,
            @JsonProperty("paymentReference") String paymentReference) {
        this.paymentId = paymentId;
        this.bookingId = bookingId;
        this.userId = userId;
        this.eventId = eventId;
        this.amount = amount;
        this.status = status;
        this.completedAt = completedAt;
        this.paymentReference = paymentReference;
    }
}

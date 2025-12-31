package com.backend.shared.exceptions;

import java.time.Instant;
import java.util.UUID;

/**
 * Exception thrown when a seat lock has expired during the checkout process.
 * This occurs when a user takes too long to complete payment.
 */
public class SeatLockExpiredException extends BusinessException {

    private static final String ERROR_CODE = "SEAT_LOCK_EXPIRED";

    /**
     * Creates a new SeatLockExpiredException
     * 
     * @param eventId        The ID of the event
     * @param seatIdentifier The identifier of the seat
     * @param expirationTime When the lock expired
     */
    public SeatLockExpiredException(UUID eventId, String seatIdentifier, Instant expirationTime) {
        super(
                ERROR_CODE,
                String.format("Lock for seat %s (event %s) expired at %s. Please select seats again.",
                        seatIdentifier, eventId, expirationTime));
    }

    /**
     * Creates a new SeatLockExpiredException with custom message
     * 
     * @param message Custom error message
     */
    public SeatLockExpiredException(String message) {
        super(ERROR_CODE, message);
    }
}

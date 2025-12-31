package com.backend.shared.exceptions;

import java.util.UUID;

/**
 * Exception thrown when attempting to book a seat that is already booked.
 * This is a critical business rule violation in the ticketing system.
 */
public class SeatAlreadyBookedException extends BusinessException {

    private static final String ERROR_CODE = "SEAT_ALREADY_BOOKED";

    /**
     * Creates a new SeatAlreadyBookedException
     * 
     * @param eventId        The ID of the event
     * @param seatIdentifier The identifier of the seat (row-column or label)
     */
    public SeatAlreadyBookedException(UUID eventId, String seatIdentifier) {
        super(
                ERROR_CODE,
                String.format("Seat %s for event %s is already booked", seatIdentifier, eventId));
    }

    /**
     * Creates a new SeatAlreadyBookedException with custom message
     * 
     * @param message Custom error message
     */
    public SeatAlreadyBookedException(String message) {
        super(ERROR_CODE, message);
    }
}

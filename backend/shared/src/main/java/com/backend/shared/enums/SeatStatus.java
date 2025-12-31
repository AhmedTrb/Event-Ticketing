package com.backend.shared.enums;

/**
 * Represents the current availability state of a seat.
 * Critical for preventing double-booking and managing seat reservations.
 */
public enum SeatStatus {
    /**
     * Seat is available for booking
     */
    AVAILABLE,

    /**
     * Seat is temporarily locked during a booking session (with TTL)
     */
    LOCKED,

    /**
     * Seat has been permanently booked
     */
    BOOKED
}

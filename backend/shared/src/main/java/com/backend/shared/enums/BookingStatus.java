package com.backend.shared.enums;

/**
 * Represents the status of a ticket booking/registration.
 * Tracks the booking lifecycle from creation to finalization.
 */
public enum BookingStatus {
    /**
     * Booking has been created but not yet confirmed (awaiting payment)
     */
    PENDING,

    /**
     * Booking has been confirmed and ticket issued
     */
    CONFIRMED,

    /**
     * Booking has been cancelled by user or system
     */
    CANCELLED
}

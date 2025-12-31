package com.backend.shared.enums;

/**
 * Represents the state of a payment transaction.
 * Tracks payment lifecycle from initiation to completion or failure.
 */
public enum PaymentStatus {
    /**
     * Payment has been initiated but not yet completed
     */
    PENDING,

    /**
     * Payment has been successfully completed
     */
    COMPLETED,

    /**
     * Payment has failed
     */
    FAILED,

    /**
     * Payment has been refunded
     */
    REFUNDED
}

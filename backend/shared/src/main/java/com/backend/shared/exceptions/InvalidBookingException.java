package com.backend.shared.exceptions;

/**
 * Exception thrown for invalid booking operations.
 * This includes various booking-related business rule violations.
 */
public class InvalidBookingException extends BusinessException {

    private static final String ERROR_CODE = "INVALID_BOOKING";

    /**
     * Creates a new InvalidBookingException
     * 
     * @param message Error message describing what is invalid
     */
    public InvalidBookingException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * Creates a new InvalidBookingException with cause
     * 
     * @param message Error message describing what is invalid
     * @param cause   The underlying cause
     */
    public InvalidBookingException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}

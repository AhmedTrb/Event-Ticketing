package com.backend.shared.exceptions;

import lombok.Getter;

/**
 * Base exception for all technical/infrastructure failures in the event
 * ticketing system.
 * This exception should be thrown when external systems or infrastructure
 * components fail.
 * 
 * Examples:
 * - Database connection failures
 * - Message broker unavailability
 * - Cache service failures
 * - External API failures
 */
@Getter
public class TechnicalException extends RuntimeException {

    /**
     * Error code for categorizing the technical exception
     */
    private final String errorCode;

    /**
     * Creates a new TechnicalException with error code and message
     * 
     * @param errorCode Technical error code for logging and debugging
     * @param message   Detailed technical error message
     */
    public TechnicalException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Creates a new TechnicalException with error code, message and cause
     * 
     * @param errorCode Technical error code for logging and debugging
     * @param message   Detailed technical error message
     * @param cause     The underlying cause of this exception
     */
    public TechnicalException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}

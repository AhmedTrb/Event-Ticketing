package com.backend.shared.exceptions;

import lombok.Getter;

/**
 * Base exception for all business logic violations in the event ticketing
 * system.
 * This exception should be thrown when domain rules or business constraints are
 * violated.
 * 
 * Examples:
 * - Attempting to book an already booked seat
 * - Trying to cancel a non-cancellable booking
 * - Invalid booking state transitions
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * Error code for categorizing the business exception
     */
    private final String errorCode;

    /**
     * User-friendly error message
     */
    private final String userMessage;

    /**
     * Creates a new BusinessException with error code and user message
     * 
     * @param errorCode   Technical error code for logging and debugging
     * @param userMessage User-friendly message to display to end users
     */
    public BusinessException(String errorCode, String userMessage) {
        super(userMessage);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    /**
     * Creates a new BusinessException with error code, user message and cause
     * 
     * @param errorCode   Technical error code for logging and debugging
     * @param userMessage User-friendly message to display to end users
     * @param cause       The underlying cause of this exception
     */
    public BusinessException(String errorCode, String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }
}

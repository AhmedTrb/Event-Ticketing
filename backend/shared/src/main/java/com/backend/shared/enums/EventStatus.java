package com.backend.shared.enums;

/**
 * Represents the current lifecycle state of an event.
 * Supports event lifecycle management from creation to completion.
 */
public enum EventStatus {
    /**
     * Event is scheduled but hasn't started yet
     */
    UPCOMING,

    /**
     * Event is currently in progress
     */
    ONGOING,

    /**
     * Event has finished
     */
    COMPLETED,

    /**
     * Event has been cancelled
     */
    CANCELLED
}

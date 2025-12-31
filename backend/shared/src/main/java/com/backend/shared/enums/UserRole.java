package com.backend.shared.enums;

/**
 * Represents user access level in the system.
 * Used for authorization and role-based access control.
 */
public enum UserRole {
    /**
     * Regular user who can browse events and book tickets
     */
    USER,

    /**
     * Administrator with elevated privileges for event and venue management
     */
    ADMIN
}

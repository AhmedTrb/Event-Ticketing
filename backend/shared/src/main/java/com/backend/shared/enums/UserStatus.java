package com.backend.shared.enums;

/**
 * Represents the state of a user account.
 * Used for account management and access control.
 */
public enum UserStatus {
    /**
     * User account is active and can access the system
     */
    ACTIVE,

    /**
     * User account has been suspended (cannot access the system)
     */
    SUSPENDED
}

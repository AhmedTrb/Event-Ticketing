package com.backend.shared.util;

import java.util.UUID;

/**
 * Utility class for generating unique identifiers.
 * Provides centralized ID generation for consistency.
 */
public final class IdGenerator {

    private IdGenerator() {
        // Prevent instantiation
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Generates a new random UUID
     * 
     * @return A new UUID
     */
    public static UUID generate() {
        return UUID.randomUUID();
    }

    /**
     * Generates a new UUID as a string
     * 
     * @return A new UUID string
     */
    public static String generateString() {
        return UUID.randomUUID().toString();
    }

    /**
     * Parses a UUID from a string
     * 
     * @param uuid The UUID string to parse
     * @return The parsed UUID
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static UUID fromString(String uuid) {
        return UUID.fromString(uuid);
    }
}

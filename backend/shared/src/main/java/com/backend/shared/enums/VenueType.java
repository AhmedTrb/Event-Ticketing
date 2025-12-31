package com.backend.shared.enums;

/**
 * Represents the type of venue.
 * Determines the seating layout strategy (procedural vs SVG-based).
 */
public enum VenueType {
    /**
     * Movie theatre with procedurally generated rectangular seating
     */
    MOVIE_THEATRE,

    /**
     * Stadium with SVG-based seating maps
     */
    STADIUM,

    /**
     * Concert hall with SVG-based seating maps
     */
    CONCERT_HALL,

    /**
     * Other venue types
     */
    OTHER
}

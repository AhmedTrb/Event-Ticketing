package com.backend.ticketingapi.domain.venue;

import com.backend.shared.enums.VenueType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a physical venue where events take place.
 * Can be a movie theatre (procedural seating) or stadium/hall (SVG/Map based).
 */
@Entity
@Table(name = "venues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private VenueType type;

    /**
     * Total capacity of the venue.
     */
    @Column(nullable = false)
    private int capacity;

    /**
     * Number of rows (for procedural venues like Movie Theatres).
     * Null for non-procedural venues.
     */
    @Column(name = "row_count")
    private Integer rowCount;

    /**
     * Number of columns (for procedural venues like Movie Theatres).
     * Null for non-procedural venues.
     */
    @Column(name = "col_count")
    private Integer colCount;

    /**
     * JSON or BLOB storage for complex seating maps (SVG data or custom JSON).
     * Used for Stadiums and Concert Halls.
     */
    @Column(name = "seating_map", columnDefinition = "TEXT")
    private String seatingMap;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

package com.backend.ticketingapi.repository;

import com.backend.ticketingapi.domain.venue.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for Venue entity operations.
 */
@Repository
public interface VenueRepository extends JpaRepository<Venue, UUID> {

    // Custom query methods can be added here if needed
    boolean existsByName(String name);
}

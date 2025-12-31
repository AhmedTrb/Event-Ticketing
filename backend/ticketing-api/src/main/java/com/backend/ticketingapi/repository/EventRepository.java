package com.backend.ticketingapi.repository;

import com.backend.shared.enums.EventStatus;
import com.backend.shared.enums.EventType;
import com.backend.ticketingapi.domain.event.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for Event entity operations.
 */
@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    /**
     * Find events by venue.
     */
    List<Event> findByVenueId(UUID venueId);

    /**
     * Find events by status.
     */
    List<Event> findByStatus(EventStatus status);

    /**
     * Find upcoming events (start date in future) that are not cancelled.
     */
    @Query("SELECT e FROM Event e WHERE e.startDate > :now AND e.status != 'CANCELLED'")
    Page<Event> findUpcomingEvents(@Param("now") Instant now, Pageable pageable);

    /**
     * Find events by type.
     */
    Page<Event> findByEventType(EventType eventType, Pageable pageable);
}

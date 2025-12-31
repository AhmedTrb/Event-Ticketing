package com.backend.ticketingapi.repository;

import com.backend.ticketingapi.domain.event.Performer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for Performer entity operations.
 */
@Repository
public interface PerformerRepository extends JpaRepository<Performer, UUID> {

    boolean existsByName(String name);
}

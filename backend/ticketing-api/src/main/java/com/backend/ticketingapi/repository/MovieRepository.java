package com.backend.ticketingapi.repository;

import com.backend.ticketingapi.domain.event.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for Movie entity operations.
 */
@Repository
public interface MovieRepository extends JpaRepository<Movie, UUID> {

    boolean existsByTitle(String title);
}

package com.backend.registrationworker.repository;

import com.backend.registrationworker.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    boolean existsByBookingId(UUID bookingId);

    long countByEventId(UUID eventId);
}

package com.backend.ticketingapi.service;

import com.backend.shared.enums.BookingStatus;
import com.backend.shared.messaging.commands.ProcessBookingCommand;
import com.backend.shared.util.IdGenerator;
import com.backend.ticketingapi.domain.event.Event;
import com.backend.ticketingapi.domain.user.User;
import com.backend.ticketingapi.dto.request.CreateEventRequest;
import com.backend.ticketingapi.messaging.RegistrationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private final SeatLockService seatLockService;
    private final RegistrationPublisher registrationPublisher;
    // We would need a BookingRepository here to save the initial PENDING booking
    // private final BookingRepository bookingRepository;

    /**
     * Handles Simple Registration (Direct Seat Locking).
     * Used for low-demand events or direct purchases.
     */
    @Transactional
    public UUID registerSimple(UUID userId, UUID eventId, List<String> seatIdentifiers) {
        log.info("Starting simple registration for user {} on event {}", userId, eventId);

        // 1. Lock Seats in Redis (Fail fast if already taken)
        seatLockService.lockSeats(eventId, seatIdentifiers, userId);

        // 2. Create PENDING Booking in DB (Skipping DB implementation for this step as
        // repository not created yet)
        // Booking booking = new Booking(); ... save(booking);
        UUID bookingId = IdGenerator.generate(); // Placeholder

        // 3. (Optional) Publish event or return success for payment diversion
        log.info("Seats locked successfully. Proceed to payment for booking {}", bookingId);

        return bookingId;
    }

    /**
     * Handles Queued Registration.
     * Pushes the request to RabbitMQ for async processing.
     */
    public UUID registerViaQueue(UUID userId, UUID eventId, List<String> seatIdentifiers) {
        log.info("Queueing registration for user {} on event {}", userId, eventId);

        UUID bookingId = IdGenerator.generate();

        // Create Command
        ProcessBookingCommand command = new ProcessBookingCommand(
                bookingId,
                userId,
                eventId,
                seatIdentifiers,
                true // fromQueue
        );

        // Publish to RabbitMQ
        registrationPublisher.publishRegistrationRequest(command);

        return bookingId;
    }
}

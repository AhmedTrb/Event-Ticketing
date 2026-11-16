package com.backend.registrationworker.service;

import com.backend.registrationworker.domain.Ticket;
import com.backend.registrationworker.messaging.NotificationPublisher;
import com.backend.registrationworker.repository.TicketRepository;
import com.backend.shared.messaging.commands.ProcessBookingCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Processes booking commands consumed from RabbitMQ.
 *
 * <ol>
 * <li>Verifies that every seat is still locked <b>by the requesting user</b>
 * (lock taken synchronously by ticketing-api).</li>
 * <li>Persists the tickets atomically (unique (event, seat) constraint is the
 * last line of defence against overbooking).</li>
 * <li>Converts the temporary TTL lock into a permanent "booked" marker so the
 * seat can never be locked again.</li>
 * <li>Publishes a success / failure notification.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerBookingService {

    static final String LOCK_OWNER_PREFIX = "locked_by_";
    static final String BOOKED_OWNER_PREFIX = "booked_by_";

    private final RedisTemplate<String, Object> redisTemplate;
    private final TicketRepository ticketRepository;
    private final NotificationPublisher notificationPublisher;

    public void processBooking(ProcessBookingCommand command) {
        log.info("Processing booking: {}", command.getBookingId());

        // Idempotency: RabbitMQ is at-least-once, never create tickets twice
        if (ticketRepository.existsByBookingId(command.getBookingId())) {
            log.warn("Booking {} already processed, ignoring duplicate delivery", command.getBookingId());
            return;
        }

        // 1. Verify seat locks in Redis are still held by this user
        if (!verifyLocks(command)) {
            log.error("Locks expired or invalid for booking {}", command.getBookingId());
            notificationPublisher.publishBookingFailure(command.getUserId(), command.getBookingId(),
                    command.getEventId(), "Locks expired");
            return;
        }

        // 2. Persist tickets atomically
        try {
            saveBookingToDb(command);
        } catch (DataIntegrityViolationException e) {
            log.error("Seat conflict for booking {}", command.getBookingId(), e);
            notificationPublisher.publishBookingFailure(command.getUserId(), command.getBookingId(),
                    command.getEventId(), "Seat already booked");
            return;
        } catch (RuntimeException e) {
            log.error("Failed to save tickets for booking {}", command.getBookingId(), e);
            releaseLocks(command);
            notificationPublisher.publishBookingFailure(command.getUserId(), command.getBookingId(),
                    command.getEventId(), "Database error");
            return;
        }

        // 3. Lock -> permanent booked marker
        markSeatsBooked(command);

        // 4. Notify the user
        notificationPublisher.publishBookingConfirmation(command.getUserId(), command.getBookingId(),
                command.getEventId());

        log.info("Booking {} confirmed and processed.", command.getBookingId());
    }

    private boolean verifyLocks(ProcessBookingCommand command) {
        String expectedOwner = LOCK_OWNER_PREFIX + command.getUserId();
        for (String seatId : command.getSelectedSeats()) {
            Object owner = redisTemplate.opsForValue().get(lockKey(command, seatId));
            if (owner == null || !expectedOwner.equals(owner.toString())) {
                return false;
            }
        }
        return true;
    }

    private void saveBookingToDb(ProcessBookingCommand command) {
        List<Ticket> tickets = command.getSelectedSeats().stream()
                .map(seatId -> Ticket.builder()
                        .bookingId(command.getBookingId())
                        .userId(command.getUserId())
                        .eventId(command.getEventId())
                        .seatId(seatId)
                        .status("CONFIRMED")
                        .build())
                .toList();

        ticketRepository.saveAllAndFlush(tickets);
        log.info("Saved {} tickets for booking {}", tickets.size(), command.getBookingId());
    }

    private void markSeatsBooked(ProcessBookingCommand command) {
        String value = BOOKED_OWNER_PREFIX + command.getUserId();
        for (String seatId : command.getSelectedSeats()) {
            // No TTL: the seat stays taken for good
            redisTemplate.opsForValue().set(lockKey(command, seatId), value);
        }
    }

    private void releaseLocks(ProcessBookingCommand command) {
        for (String seatId : command.getSelectedSeats()) {
            redisTemplate.delete(lockKey(command, seatId));
        }
    }

    private String lockKey(ProcessBookingCommand command, String seatId) {
        return "seat_lock:" + command.getEventId() + ":" + seatId;
    }
}

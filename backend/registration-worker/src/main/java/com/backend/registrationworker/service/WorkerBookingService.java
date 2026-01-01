package com.backend.registrationworker.service;

import com.backend.registrationworker.messaging.NotificationPublisher;
import com.backend.shared.messaging.commands.ProcessBookingCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerBookingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final com.backend.registrationworker.repository.TicketRepository ticketRepository;
    private final NotificationPublisher notificationPublisher;

    @Transactional
    public void processBooking(ProcessBookingCommand command) {
        log.info("Processing booking: {}", command.getBookingId());

        // 1. Verify Seat Locks in Redis
        boolean allSeatsLocked = verifyLocks(command);

        if (!allSeatsLocked) {
            log.error("Locks expired or invalid for booking {}", command.getBookingId());
            notificationPublisher.publishBookingFailure(command.getUserId(), command.getBookingId(),
                    command.getEventId(), "Locks expired");
            return;
        }

        // 2. Create Tickets / Confirm Booking in DB
        try {
            saveBookingToDb(command);
        } catch (Exception e) {
            log.error("Failed to save tickets for booking {}", command.getBookingId(), e);
            notificationPublisher.publishBookingFailure(command.getUserId(), command.getBookingId(),
                    command.getEventId(), "Database error");
            throw e;
        }

        // 3. Publish Notification
        notificationPublisher.publishBookingConfirmation(command.getUserId(), command.getBookingId(),
                command.getEventId());

        log.info("Booking {} confirmed and processed.", command.getBookingId());
    }

    private boolean verifyLocks(ProcessBookingCommand command) {
        for (String seatId : command.getSelectedSeats()) {
            String lockKey = "seat_lock:" + command.getEventId() + ":" + seatId;
            Boolean hasKey = redisTemplate.hasKey(lockKey);
            if (Boolean.FALSE.equals(hasKey)) {
                return false;
            }
        }
        return true;
    }

    private void saveBookingToDb(ProcessBookingCommand command) {
        java.util.List<com.backend.registrationworker.domain.Ticket> tickets = command.getSelectedSeats().stream()
                .map(seatId -> com.backend.registrationworker.domain.Ticket.builder()
                        .bookingId(command.getBookingId())
                        .userId(command.getUserId())
                        .eventId(command.getEventId())
                        .seatId(seatId)
                        .status("CONFIRMED")
                        .build())
                .collect(java.util.stream.Collectors.toList());

        ticketRepository.saveAll(tickets);
        log.info("Saved {} tickets for booking {}", tickets.size(), command.getBookingId());
    }
}

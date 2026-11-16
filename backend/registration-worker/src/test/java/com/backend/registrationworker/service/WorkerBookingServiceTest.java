package com.backend.registrationworker.service;

import com.backend.registrationworker.domain.Ticket;
import com.backend.registrationworker.messaging.NotificationPublisher;
import com.backend.registrationworker.repository.TicketRepository;
import com.backend.shared.messaging.commands.ProcessBookingCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerBookingServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOps;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private NotificationPublisher notificationPublisher;

    private WorkerBookingService service;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private ProcessBookingCommand command;

    @BeforeEach
    void setUp() {
        service = new WorkerBookingService(redisTemplate, ticketRepository, notificationPublisher);
        command = new ProcessBookingCommand(bookingId, userId, eventId, List.of("A1", "A2"), true);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private String key(String seat) {
        return "seat_lock:" + eventId + ":" + seat;
    }

    private void seatsLockedByUser() {
        when(valueOps.get(key("A1"))).thenReturn("locked_by_" + userId);
        when(valueOps.get(key("A2"))).thenReturn("locked_by_" + userId);
    }

    @Test
    @DisplayName("valid locks -> tickets saved CONFIRMED, seats permanently marked booked, confirmation published")
    @SuppressWarnings("unchecked")
    void processBooking_happyPath() {
        seatsLockedByUser();

        service.processBooking(command);

        ArgumentCaptor<List<Ticket>> captor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).hasSize(2).allSatisfy(t -> {
            assertThat(t.getStatus()).isEqualTo("CONFIRMED");
            assertThat(t.getBookingId()).isEqualTo(bookingId);
            assertThat(t.getUserId()).isEqualTo(userId);
            assertThat(t.getEventId()).isEqualTo(eventId);
        });
        assertThat(captor.getValue()).extracting(Ticket::getSeatId).containsExactly("A1", "A2");
        // permanent marker: plain set(key, value) with no TTL
        verify(valueOps).set(key("A1"), "booked_by_" + userId);
        verify(valueOps).set(key("A2"), "booked_by_" + userId);
        verify(notificationPublisher).publishBookingConfirmation(userId, bookingId, eventId);
    }

    @Test
    @DisplayName("missing/expired lock -> failure notification, nothing persisted")
    void processBooking_lockExpired() {
        when(valueOps.get(key("A1"))).thenReturn(null);

        service.processBooking(command);

        verify(notificationPublisher).publishBookingFailure(userId, bookingId, eventId, "Locks expired");
        verify(ticketRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("seat locked by a different user is rejected")
    void processBooking_lockOwnedBySomeoneElse() {
        when(valueOps.get(key("A1"))).thenReturn("locked_by_" + UUID.randomUUID());

        service.processBooking(command);

        verify(notificationPublisher).publishBookingFailure(userId, bookingId, eventId, "Locks expired");
        verify(ticketRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("duplicate RabbitMQ delivery is ignored (idempotent)")
    void processBooking_duplicateDelivery() {
        when(ticketRepository.existsByBookingId(bookingId)).thenReturn(true);

        service.processBooking(command);

        verify(ticketRepository, never()).saveAllAndFlush(anyList());
        verifyNoInteractions(notificationPublisher);
    }

    @Test
    @DisplayName("unique-constraint violation -> 'Seat already booked', locks untouched")
    void processBooking_seatConflict() {
        seatsLockedByUser();
        doThrow(new DataIntegrityViolationException("uk_ticket_event_seat")).when(ticketRepository)
                .saveAllAndFlush(anyList());

        service.processBooking(command);

        verify(notificationPublisher).publishBookingFailure(userId, bookingId, eventId, "Seat already booked");
        verify(redisTemplate, never()).delete(anyString());
        verify(valueOps, never()).set(anyString(), eq("booked_by_" + userId));
        verify(notificationPublisher, never()).publishBookingConfirmation(userId, bookingId, eventId);
    }

    @Test
    @DisplayName("unexpected persistence error -> locks released and 'Database error' notification")
    void processBooking_databaseError() {
        seatsLockedByUser();
        doThrow(new IllegalStateException("db down")).when(ticketRepository).saveAllAndFlush(anyList());

        service.processBooking(command);

        verify(redisTemplate).delete(key("A1"));
        verify(redisTemplate).delete(key("A2"));
        verify(notificationPublisher).publishBookingFailure(userId, bookingId, eventId, "Database error");
    }
}

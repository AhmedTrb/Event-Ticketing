package com.backend.ticketingapi.service;

import com.backend.shared.exceptions.SeatAlreadyBookedException;
import com.backend.shared.messaging.commands.ProcessBookingCommand;
import com.backend.ticketingapi.messaging.RegistrationPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private SeatLockService seatLockService;
    @Mock
    private RegistrationPublisher registrationPublisher;
    @InjectMocks
    private RegistrationService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final List<String> seats = List.of("A1", "A2");

    @Test
    @DisplayName("registerSimple locks the seats and returns a booking id without queueing")
    void registerSimple_locksSeatsOnly() {
        UUID bookingId = service.registerSimple(userId, eventId, seats);

        assertThat(bookingId).isNotNull();
        verify(seatLockService).lockSeats(eventId, seats, userId);
        verify(registrationPublisher, never()).publishRegistrationRequest(any());
    }

    @Test
    @DisplayName("registerSimple propagates a seat conflict")
    void registerSimple_conflict_propagates() {
        doThrow(new SeatAlreadyBookedException(eventId, "A1")).when(seatLockService).lockSeats(eventId, seats, userId);

        assertThatThrownBy(() -> service.registerSimple(userId, eventId, seats))
                .isInstanceOf(SeatAlreadyBookedException.class);
    }

    @Test
    @DisplayName("registerViaQueue locks first, then publishes a fromQueue command carrying the tracking id")
    void registerViaQueue_locksThenPublishes() {
        UUID bookingId = service.registerViaQueue(userId, eventId, seats);

        InOrder order = inOrder(seatLockService, registrationPublisher);
        order.verify(seatLockService).lockSeats(eventId, seats, userId);
        ArgumentCaptor<ProcessBookingCommand> captor = ArgumentCaptor.forClass(ProcessBookingCommand.class);
        order.verify(registrationPublisher).publishRegistrationRequest(captor.capture());

        ProcessBookingCommand command = captor.getValue();
        assertThat(command.getBookingId()).isEqualTo(bookingId);
        assertThat(command.getUserId()).isEqualTo(userId);
        assertThat(command.getEventId()).isEqualTo(eventId);
        assertThat(command.getSelectedSeats()).containsExactlyElementsOf(seats);
        assertThat(command.isFromQueue()).isTrue();
    }

    @Test
    @DisplayName("registerViaQueue never publishes when seats are already taken (409 path)")
    void registerViaQueue_conflict_doesNotPublish() {
        doThrow(new SeatAlreadyBookedException(eventId, "A1")).when(seatLockService).lockSeats(eventId, seats, userId);

        assertThatThrownBy(() -> service.registerViaQueue(userId, eventId, seats))
                .isInstanceOf(SeatAlreadyBookedException.class);

        verify(registrationPublisher, never()).publishRegistrationRequest(any());
    }

    @Test
    @DisplayName("registerViaQueue releases the seats when the broker is unavailable")
    void registerViaQueue_publishFails_releasesSeats() {
        doThrow(new IllegalStateException("broker down")).when(registrationPublisher)
                .publishRegistrationRequest(any());

        assertThatThrownBy(() -> service.registerViaQueue(userId, eventId, seats))
                .isInstanceOf(IllegalStateException.class);

        verify(seatLockService).unlockSeats(eventId, seats, userId);
    }
}

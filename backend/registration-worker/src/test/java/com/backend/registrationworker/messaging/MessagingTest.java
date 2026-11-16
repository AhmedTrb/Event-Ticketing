package com.backend.registrationworker.messaging;

import com.backend.registrationworker.service.WorkerBookingService;
import com.backend.shared.messaging.commands.ProcessBookingCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MessagingTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private WorkerBookingService workerBookingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID userId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @Test
    @DisplayName("confirmation goes to the 'notifications' channel (the one notification-service subscribes to) as JSON")
    void publisher_confirmation() throws Exception {
        NotificationPublisher publisher = new NotificationPublisher(redisTemplate, objectMapper);

        publisher.publishBookingConfirmation(userId, bookingId, eventId);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(redisTemplate).convertAndSend(eq("notifications"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(String.class);
        JsonNode json = objectMapper.readTree((String) payload.getValue());
        assertThat(json.get("type").asText()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(json.get("userId").asText()).isEqualTo(userId.toString());
        assertThat(json.get("bookingId").asText()).isEqualTo(bookingId.toString());
        assertThat(json.get("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(json.get("message").asText()).isNotBlank();
    }

    @Test
    @DisplayName("failure notification carries the reason")
    void publisher_failure() throws Exception {
        NotificationPublisher publisher = new NotificationPublisher(redisTemplate, objectMapper);

        publisher.publishBookingFailure(userId, bookingId, eventId, "Locks expired");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(redisTemplate).convertAndSend(eq(NotificationPublisher.REDIS_CHANNEL), payload.capture());
        JsonNode json = objectMapper.readTree((String) payload.getValue());
        assertThat(json.get("type").asText()).isEqualTo("BOOKING_FAILED");
        assertThat(json.get("message").asText()).contains("Locks expired");
    }

    @Test
    @DisplayName("consumer delegates the command to the booking service")
    void consumer_delegates() {
        RegistrationConsumer consumer = new RegistrationConsumer(workerBookingService);
        ProcessBookingCommand command = new ProcessBookingCommand(bookingId, userId, eventId, List.of("A1"), true);

        consumer.consumeRegistrationRequest(command);

        verify(workerBookingService).processBooking(command);
    }

    @Test
    @DisplayName("consumer never lets a poison message escape (no endless requeue loop)")
    void consumer_swallowsErrors() {
        RegistrationConsumer consumer = new RegistrationConsumer(workerBookingService);
        ProcessBookingCommand command = new ProcessBookingCommand(bookingId, userId, eventId, List.of("A1"), true);
        doThrow(new IllegalStateException("boom")).when(workerBookingService).processBooking(command);

        assertThatCode(() -> consumer.consumeRegistrationRequest(command)).doesNotThrowAnyException();
    }
}

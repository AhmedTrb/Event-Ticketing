package com.backend.notificationservice;

import com.backend.notificationservice.domain.Notification;
import com.backend.notificationservice.messaging.NotificationListener;
import com.backend.notificationservice.repository.NotificationRepository;
import com.backend.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationFlowTest {

    @Mock
    private NotificationRepository repository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("a booking notification is stored unread and pushed to the user's WebSocket topic")
    void processNotification_savesAndPushes() {
        NotificationService service = new NotificationService(repository, messagingTemplate, objectMapper);
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Map<String, Object> data = Map.of("userId", userId.toString(), "message", "Your booking has been confirmed!",
                "type", "BOOKING_CONFIRMED", "bookingId", bookingId.toString(), "eventId", eventId.toString());

        service.processNotification(data);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getBookingId()).isEqualTo(bookingId);
        assertThat(saved.getValue().getEventId()).isEqualTo(eventId);
        assertThat(saved.getValue().getType()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(saved.getValue().isRead()).isFalse();
        verify(messagingTemplate).convertAndSend(eq("/topic/user/" + userId + "/notifications"),
                org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    @DisplayName("notifications missing userId or message are ignored")
    void processNotification_invalidIgnored() {
        NotificationService service = new NotificationService(repository, messagingTemplate, objectMapper);
        Map<String, Object> noMessage = new HashMap<>();
        noMessage.put("userId", UUID.randomUUID().toString());

        service.processNotification(noMessage);
        service.processNotification(Map.of("message", "hi"));

        verify(repository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), org.mockito.ArgumentMatchers.<Object>any());
    }

    @Test
    @DisplayName("a malformed userId never crashes the Redis listener thread")
    void processNotification_badUuidSwallowed() {
        NotificationService service = new NotificationService(repository, messagingTemplate, objectMapper);

        assertThatCode(() -> service.processNotification(Map.of("userId", "not-a-uuid", "message", "x")))
                .doesNotThrowAnyException();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("listener parses the JSON string received from Redis and delegates")
    void listener_parsesJson() {
        NotificationService service = mock(NotificationService.class);
        NotificationListener listener = new NotificationListener(service, objectMapper);
        UUID userId = UUID.randomUUID();

        listener.onMessage("{\"userId\":\"" + userId + "\",\"message\":\"ok\",\"type\":\"BOOKING_CONFIRMED\"}",
                "notifications");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(service).processNotification(captor.capture());
        assertThat(captor.getValue()).containsEntry("userId", userId.toString()).containsEntry("message", "ok");
    }

    @Test
    @DisplayName("listener drops malformed JSON without throwing")
    void listener_malformedJson() {
        NotificationService service = mock(NotificationService.class);
        NotificationListener listener = new NotificationListener(service, objectMapper);

        assertThatCode(() -> listener.onMessage("{not json", "notifications")).doesNotThrowAnyException();
        verify(service, never()).processNotification(anyMap());
    }
}

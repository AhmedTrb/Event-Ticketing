package com.backend.ticketingapi.service;

import com.backend.shared.exceptions.SeatAlreadyBookedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatLockServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOps;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private SeatLockService service;

    private final UUID eventId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SeatLockService(redisTemplate, messagingTemplate);
    }

    @Test
    @DisplayName("lockSeats sets an atomic 10-minute lock per seat owned by the user and broadcasts LOCKED")
    void lockSeats_allFree_locksEachSeatWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);

        service.lockSeats(eventId, List.of("A1", "A2"), userId);

        verify(valueOps).setIfAbsent("seat_lock:" + eventId + ":A1", "locked_by_" + userId, Duration.ofSeconds(600));
        verify(valueOps).setIfAbsent("seat_lock:" + eventId + ":A2", "locked_by_" + userId, Duration.ofSeconds(600));
        verify(messagingTemplate).convertAndSend("/topic/events/" + eventId + "/seats",
                Map.of("seatId", "A1", "status", "LOCKED", "userId", userId));
        verify(messagingTemplate).convertAndSend("/topic/events/" + eventId + "/seats",
                Map.of("seatId", "A2", "status", "LOCKED", "userId", userId));
    }

    @Test
    @DisplayName("lockSeats throws SeatAlreadyBooked and rolls back only the seats it locked itself")
    void lockSeats_secondSeatTaken_rollsBackOnlyOwnLocks() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true, false);
        when(valueOps.get(anyString())).thenReturn("locked_by_someone-else");

        assertThatThrownBy(() -> service.lockSeats(eventId, List.of("A1", "A2"), userId))
                .isInstanceOf(SeatAlreadyBookedException.class)
                .hasMessageContaining("A2");

        verify(redisTemplate).delete("seat_lock:" + eventId + ":A1");
        // the seat held by the other user must never be released
        verify(redisTemplate, never()).delete("seat_lock:" + eventId + ":A2");
    }

    @Test
    @DisplayName("unlockSeats deletes the lock and broadcasts AVAILABLE")
    void unlockSeats_deletesAndBroadcasts() {
        service.unlockSeats(eventId, List.of("B7"), userId);

        verify(redisTemplate).delete("seat_lock:" + eventId + ":B7");
        verify(messagingTemplate).convertAndSend("/topic/events/" + eventId + "/seats",
                Map.of("seatId", "B7", "status", "AVAILABLE"));
    }

    @Test
    @DisplayName("isSeatLocked reflects key existence")
    void isSeatLocked_reflectsRedisKey() {
        when(redisTemplate.hasKey("seat_lock:" + eventId + ":C1")).thenReturn(true);
        when(redisTemplate.hasKey("seat_lock:" + eventId + ":C2")).thenReturn(false);

        assertThat(service.isSeatLocked(eventId, "C1")).isTrue();
        assertThat(service.isSeatLocked(eventId, "C2")).isFalse();
    }
}

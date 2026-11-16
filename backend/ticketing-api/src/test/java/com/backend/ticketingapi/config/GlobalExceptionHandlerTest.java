package com.backend.ticketingapi.config;

import com.backend.shared.exceptions.BusinessException;
import com.backend.shared.exceptions.SeatAlreadyBookedException;
import com.backend.shared.exceptions.SeatLockExpiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("seat already booked -> 409 Conflict (README contract)")
    void seatTaken_is409() {
        ResponseEntity<Map<String, Object>> response = handler
                .handleSeatTaken(new SeatAlreadyBookedException(UUID.randomUUID(), "A1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "SEAT_ALREADY_BOOKED");
    }

    @Test
    @DisplayName("expired lock -> 410 Gone")
    void lockExpired_is410() {
        assertThat(handler.handleLockExpired(new SeatLockExpiredException("expired")).getStatusCode())
                .isEqualTo(HttpStatus.GONE);
    }

    @ParameterizedTest
    @CsvSource({
            "EVENT_NOT_FOUND, 404",
            "VENUE_EXISTS, 409",
            "INVALID_DATE_RANGE, 400"
    })
    @DisplayName("business exceptions map by error-code suffix")
    void business_mapping(String code, int expectedStatus) {
        ResponseEntity<Map<String, Object>> response = handler.handleBusiness(new BusinessException(code, "msg"));

        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).containsEntry("message", "msg");
    }

    @Test
    @DisplayName("illegal argument -> 400")
    void illegalArgument_is400() {
        assertThat(handler.handleIllegalArgument(new IllegalArgumentException("Invalid credentials"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}

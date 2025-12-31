package com.backend.ticketingapi.dto.request;

import com.backend.shared.enums.EventType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateEventRequest(
        @NotBlank(message = "Name is required") String name,

        String description,

        @NotNull(message = "Start date is required") @Future(message = "Start date must be in the future") Instant startDate,

        @NotNull(message = "End date is required") @Future(message = "End date must be in the future") Instant endDate,

        @NotNull(message = "Event type is required") EventType eventType,

        @NotNull(message = "Venue ID is required") UUID venueId,

        UUID movieId,

        UUID performerId,

        Integer maxCapacity) {
}

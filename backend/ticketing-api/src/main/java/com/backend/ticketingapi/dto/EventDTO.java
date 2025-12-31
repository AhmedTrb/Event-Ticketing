package com.backend.ticketingapi.dto;

import com.backend.shared.enums.EventStatus;
import com.backend.shared.enums.EventType;

import java.time.Instant;
import java.util.UUID;

public record EventDTO(
        UUID id,
        String name,
        String description,
        Instant startDate,
        Instant endDate,
        EventType eventType,
        EventStatus status,
        VenueDTO venue,
        MovieDTO movie,
        PerformerDTO performer,
        Integer maxCapacity) {
}

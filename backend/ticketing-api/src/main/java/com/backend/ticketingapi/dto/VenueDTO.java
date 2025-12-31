package com.backend.ticketingapi.dto;

import com.backend.shared.enums.VenueType;

import java.util.UUID;

public record VenueDTO(
        UUID id,
        String name,
        String location,
        VenueType type,
        int capacity,
        Integer rowCount,
        Integer colCount) {
}

package com.backend.ticketingapi.dto;

import java.util.UUID;

public record PerformerDTO(
        UUID id,
        String name,
        String description,
        String imageUrl) {
}

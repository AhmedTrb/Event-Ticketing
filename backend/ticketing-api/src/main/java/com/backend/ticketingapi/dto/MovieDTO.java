package com.backend.ticketingapi.dto;

import java.time.LocalDate;
import java.util.UUID;

public record MovieDTO(
        UUID id,
        String title,
        String description,
        String director,
        LocalDate releaseDate,
        Integer durationMinutes,
        String posterUrl) {
}

package com.backend.ticketingapi.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record RegistrationRequest(
        @NotNull(message = "Event ID is required") UUID eventId,

        @NotEmpty(message = "Selection of seats is required") List<String> seatIdentifiers) {
}

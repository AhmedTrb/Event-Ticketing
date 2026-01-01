package com.backend.ticketingapi.controller;

import com.backend.ticketingapi.dto.request.RegistrationRequest;
import com.backend.ticketingapi.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/register")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Registration", description = "Event Registration & Booking APIs")
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping("/simple")
    @Operation(summary = "Simple Registration", description = "Directly lock seats and proceed to payment (Low demand events)")
    public ResponseEntity<Map<String, UUID>> registerSimple(@Valid @RequestBody RegistrationRequest request,
            Authentication authentication) {
        UUID userId = extractUserId(authentication);
        UUID bookingId = registrationService.registerSimple(userId, request.eventId(), request.seatIdentifiers());
        return ResponseEntity.ok(Map.of("bookingId", bookingId));
    }

    @PostMapping("/queue")
    @Operation(summary = "Join Registration Queue", description = "Enter queue for high demand events. Returns a booking tracking ID.")
    public ResponseEntity<Map<String, UUID>> registerViaQueue(@Valid @RequestBody RegistrationRequest request,
            Authentication authentication) {
        UUID userId = extractUserId(authentication);
        UUID bookingId = registrationService.registerViaQueue(userId, request.eventId(), request.seatIdentifiers());
        return ResponseEntity.accepted().body(Map.of("bookingId", bookingId));
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            // Assuming the Subject (sub) is the UUID, or a custom claim 'user_id'
            // For Keycloak, 'sub' is usually the user ID.
            return UUID.fromString(jwt.getSubject());
        }
        // Fallback or throw exception if not JWT
        throw new IllegalStateException("User not authenticated with JWT");
    }
}

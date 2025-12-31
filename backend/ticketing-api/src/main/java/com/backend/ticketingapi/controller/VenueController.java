package com.backend.ticketingapi.controller;

import com.backend.ticketingapi.domain.venue.Venue;
import com.backend.ticketingapi.dto.VenueDTO;
import com.backend.ticketingapi.service.VenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
@Tag(name = "Venues", description = "Venue management APIs")
public class VenueController {

    private final VenueService venueService;

    @GetMapping
    @Operation(summary = "List all venues")
    public ResponseEntity<List<VenueDTO>> getAllVenues() {
        return ResponseEntity.ok(venueService.getAllVenues());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get venue by ID")
    public ResponseEntity<VenueDTO> getVenue(@PathVariable UUID id) {
        return ResponseEntity.ok(venueService.getVenue(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new venue (Admin only)")
    public ResponseEntity<VenueDTO> createVenue(@Valid @RequestBody Venue venue) {
        return ResponseEntity.status(HttpStatus.CREATED).body(venueService.createVenue(venue));
    }
}

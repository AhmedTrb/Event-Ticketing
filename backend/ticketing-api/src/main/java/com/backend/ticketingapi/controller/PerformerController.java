package com.backend.ticketingapi.controller;

import com.backend.ticketingapi.domain.event.Performer;
import com.backend.ticketingapi.dto.PerformerDTO;
import com.backend.ticketingapi.service.PerformerService;
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
@RequestMapping("/api/performers")
@RequiredArgsConstructor
@Tag(name = "Performers", description = "Performer management APIs")
public class PerformerController {

    private final PerformerService performerService;

    @GetMapping
    @Operation(summary = "List all performers")
    public ResponseEntity<List<PerformerDTO>> getAllPerformers() {
        return ResponseEntity.ok(performerService.getAllPerformers());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get performer by ID")
    public ResponseEntity<PerformerDTO> getPerformer(@PathVariable UUID id) {
        return ResponseEntity.ok(performerService.getPerformer(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new performer (Admin only)")
    public ResponseEntity<PerformerDTO> createPerformer(@Valid @RequestBody Performer performer) {
        return ResponseEntity.status(HttpStatus.CREATED).body(performerService.createPerformer(performer));
    }
}

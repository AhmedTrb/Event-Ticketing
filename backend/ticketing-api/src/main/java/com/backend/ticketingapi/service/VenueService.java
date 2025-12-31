package com.backend.ticketingapi.service;

import com.backend.shared.exceptions.BusinessException;
import com.backend.ticketingapi.domain.venue.Venue;
import com.backend.ticketingapi.dto.VenueDTO;
import com.backend.ticketingapi.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class VenueService {

    private final VenueRepository venueRepository;

    public VenueDTO createVenue(Venue venue) {
        if (venueRepository.existsByName(venue.getName())) {
            throw new BusinessException("VENUE_EXISTS", "Venue with this name already exists");
        }
        return mapToDTO(venueRepository.save(venue));
    }

    @Transactional(readOnly = true)
    public List<VenueDTO> getAllVenues() {
        return venueRepository.findAll().stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public VenueDTO getVenue(UUID id) {
        return venueRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new BusinessException("VENUE_NOT_FOUND", "Venue not found"));
    }

    private VenueDTO mapToDTO(Venue venue) {
        return new VenueDTO(
                venue.getId(),
                venue.getName(),
                venue.getLocation(),
                venue.getType(),
                venue.getCapacity(),
                venue.getRowCount(),
                venue.getColCount());
    }
}

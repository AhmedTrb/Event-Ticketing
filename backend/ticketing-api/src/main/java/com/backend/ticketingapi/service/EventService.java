package com.backend.ticketingapi.service;

import com.backend.shared.enums.EventStatus;
import com.backend.shared.exceptions.BusinessException;
import com.backend.ticketingapi.domain.event.Event;
import com.backend.ticketingapi.domain.event.Movie;
import com.backend.ticketingapi.domain.event.Performer;
import com.backend.ticketingapi.domain.venue.Venue;
import com.backend.ticketingapi.dto.EventDTO;
import com.backend.ticketingapi.dto.MovieDTO;
import com.backend.ticketingapi.dto.PerformerDTO;
import com.backend.ticketingapi.dto.VenueDTO;
import com.backend.ticketingapi.dto.request.CreateEventRequest;
import com.backend.ticketingapi.repository.EventRepository;
import com.backend.ticketingapi.repository.MovieRepository;
import com.backend.ticketingapi.repository.PerformerRepository;
import com.backend.ticketingapi.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EventService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final MovieRepository movieRepository;
    private final PerformerRepository performerRepository;

    public EventDTO createEvent(CreateEventRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessException("INVALID_DATE_RANGE", "Start date must be before end date");
        }

        Venue venue = venueRepository.findById(request.venueId())
                .orElseThrow(() -> new BusinessException("VENUE_NOT_FOUND", "Venue not found"));

        Movie movie = null;
        if (request.movieId() != null) {
            movie = movieRepository.findById(request.movieId())
                    .orElseThrow(() -> new BusinessException("MOVIE_NOT_FOUND", "Movie not found"));
        }

        Performer performer = null;
        if (request.performerId() != null) {
            performer = performerRepository.findById(request.performerId())
                    .orElseThrow(() -> new BusinessException("PERFORMER_NOT_FOUND", "Performer not found"));
        }

        Event event = Event.builder()
                .name(request.name())
                .description(request.description())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .eventType(request.eventType())
                .status(EventStatus.UPCOMING)
                .venue(venue)
                .movie(movie)
                .performer(performer)
                .maxCapacity(request.maxCapacity() != null ? request.maxCapacity() : venue.getCapacity())
                .build();

        return mapToDTO(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public Page<EventDTO> getUpcomingEvents(Pageable pageable) {
        return eventRepository.findUpcomingEvents(Instant.now(), pageable)
                .map(this::mapToDTO);
    }

    @Transactional(readOnly = true)
    public EventDTO getEvent(UUID id) {
        return eventRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new BusinessException("EVENT_NOT_FOUND", "Event not found"));
    }

    private EventDTO mapToDTO(Event event) {
        VenueDTO venueDTO = new VenueDTO(
                event.getVenue().getId(),
                event.getVenue().getName(),
                event.getVenue().getLocation(),
                event.getVenue().getType(),
                event.getVenue().getCapacity(),
                event.getVenue().getRowCount(),
                event.getVenue().getColCount());

        MovieDTO movieDTO = null;
        if (event.getMovie() != null) {
            movieDTO = new MovieDTO(
                    event.getMovie().getId(),
                    event.getMovie().getTitle(),
                    event.getMovie().getDescription(),
                    event.getMovie().getDirector(),
                    event.getMovie().getReleaseDate(),
                    event.getMovie().getDurationMinutes(),
                    event.getMovie().getPosterUrl());
        }

        PerformerDTO performerDTO = null;
        if (event.getPerformer() != null) {
            performerDTO = new PerformerDTO(
                    event.getPerformer().getId(),
                    event.getPerformer().getName(),
                    event.getPerformer().getDescription(),
                    event.getPerformer().getImageUrl());
        }

        return new EventDTO(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getStartDate(),
                event.getEndDate(),
                event.getEventType(),
                event.getStatus(),
                venueDTO,
                movieDTO,
                performerDTO,
                event.getMaxCapacity());
    }
}

package com.backend.ticketingapi.service;

import com.backend.shared.enums.EventStatus;
import com.backend.shared.enums.EventType;
import com.backend.shared.enums.VenueType;
import com.backend.shared.exceptions.BusinessException;
import com.backend.ticketingapi.domain.event.Event;
import com.backend.ticketingapi.domain.venue.Venue;
import com.backend.ticketingapi.dto.EventDTO;
import com.backend.ticketingapi.dto.request.CreateEventRequest;
import com.backend.ticketingapi.repository.EventRepository;
import com.backend.ticketingapi.repository.MovieRepository;
import com.backend.ticketingapi.repository.PerformerRepository;
import com.backend.ticketingapi.repository.VenueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private VenueRepository venueRepository;
    @Mock
    private MovieRepository movieRepository;
    @Mock
    private PerformerRepository performerRepository;
    @InjectMocks
    private EventService service;

    private final Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
    private final Instant end = start.plus(3, ChronoUnit.HOURS);

    private Venue venue() {
        return Venue.builder().id(UUID.randomUUID()).name("Arena").location("Tunis")
                .type(VenueType.STADIUM).capacity(500).build();
    }

    private CreateEventRequest request(UUID venueId, UUID movieId, Integer maxCapacity, Instant s, Instant e) {
        return new CreateEventRequest("Concert", "desc", s, e, EventType.CONCERT, venueId, movieId, null, maxCapacity);
    }

    @Test
    @DisplayName("createEvent defaults capacity to the venue capacity and starts as UPCOMING")
    void createEvent_defaultsCapacityToVenue() {
        Venue venue = venue();
        when(venueRepository.findById(venue.getId())).thenReturn(Optional.of(venue));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> {
            Event e = i.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        EventDTO dto = service.createEvent(request(venue.getId(), null, null, start, end));

        assertThat(dto.status()).isEqualTo(EventStatus.UPCOMING);
        assertThat(dto.maxCapacity()).isEqualTo(500);
        assertThat(dto.venue().name()).isEqualTo("Arena");
        assertThat(dto.movie()).isNull();
    }

    @Test
    @DisplayName("createEvent honours an explicit capacity override")
    void createEvent_explicitCapacity() {
        Venue venue = venue();
        when(venueRepository.findById(venue.getId())).thenReturn(Optional.of(venue));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));

        EventDTO dto = service.createEvent(request(venue.getId(), null, 120, start, end));

        assertThat(dto.maxCapacity()).isEqualTo(120);
    }

    @Test
    @DisplayName("createEvent rejects start after end")
    void createEvent_invalidRange() {
        assertThatThrownBy(() -> service.createEvent(request(UUID.randomUUID(), null, null, end, start)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo("INVALID_DATE_RANGE");
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("createEvent fails with VENUE_NOT_FOUND for an unknown venue")
    void createEvent_unknownVenue() {
        UUID venueId = UUID.randomUUID();
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createEvent(request(venueId, null, null, start, end)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo("VENUE_NOT_FOUND");
    }

    @Test
    @DisplayName("createEvent fails with MOVIE_NOT_FOUND for an unknown movie")
    void createEvent_unknownMovie() {
        Venue venue = venue();
        UUID movieId = UUID.randomUUID();
        when(venueRepository.findById(venue.getId())).thenReturn(Optional.of(venue));
        when(movieRepository.findById(movieId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createEvent(request(venue.getId(), movieId, null, start, end)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo("MOVIE_NOT_FOUND");
    }

    @Test
    @DisplayName("getEvent returns the mapped DTO, or EVENT_NOT_FOUND")
    void getEvent_foundAndMissing() {
        Venue venue = venue();
        Event event = Event.builder().id(UUID.randomUUID()).name("Show").startDate(start).endDate(end)
                .eventType(EventType.CONCERT).status(EventStatus.UPCOMING).venue(venue).maxCapacity(10).build();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        UUID missing = UUID.randomUUID();
        when(eventRepository.findById(missing)).thenReturn(Optional.empty());

        assertThat(service.getEvent(event.getId()).name()).isEqualTo("Show");
        assertThatThrownBy(() -> service.getEvent(missing)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Event not found");
    }

    @Test
    @DisplayName("getUpcomingEvents maps the page returned by the repository")
    void getUpcomingEvents_mapsPage() {
        Event event = Event.builder().id(UUID.randomUUID()).name("Show").startDate(start).endDate(end)
                .eventType(EventType.CONCERT).status(EventStatus.UPCOMING).venue(venue()).build();
        PageRequest pageable = PageRequest.of(0, 20);
        when(eventRepository.findUpcomingEvents(any(Instant.class), any()))
                .thenReturn(new PageImpl<>(List.of(event), pageable, 1));

        Page<EventDTO> page = service.getUpcomingEvents(pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).name()).isEqualTo("Show");
    }
}

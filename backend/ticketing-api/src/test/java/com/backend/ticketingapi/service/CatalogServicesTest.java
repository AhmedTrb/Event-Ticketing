package com.backend.ticketingapi.service;

import com.backend.shared.enums.VenueType;
import com.backend.shared.exceptions.BusinessException;
import com.backend.ticketingapi.domain.event.Movie;
import com.backend.ticketingapi.domain.event.Performer;
import com.backend.ticketingapi.domain.venue.Venue;
import com.backend.ticketingapi.repository.MovieRepository;
import com.backend.ticketingapi.repository.PerformerRepository;
import com.backend.ticketingapi.repository.VenueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Venue, Movie and Performer catalog services share the same contract. */
@ExtendWith(MockitoExtension.class)
class CatalogServicesTest {

    @Mock
    private VenueRepository venueRepository;
    @Mock
    private MovieRepository movieRepository;
    @Mock
    private PerformerRepository performerRepository;

    // ---- Venues ----

    @Test
    @DisplayName("createVenue persists a new venue and returns its DTO")
    void createVenue_ok() {
        VenueService service = new VenueService(venueRepository);
        Venue venue = Venue.builder().name("Cinema 1").location("Sfax").type(VenueType.MOVIE_THEATRE)
                .capacity(100).rowCount(10).colCount(10).build();
        when(venueRepository.existsByName("Cinema 1")).thenReturn(false);
        when(venueRepository.save(venue)).thenAnswer(i -> {
            venue.setId(UUID.randomUUID());
            return venue;
        });

        var dto = service.createVenue(venue);

        assertThat(dto.name()).isEqualTo("Cinema 1");
        assertThat(dto.rowCount()).isEqualTo(10);
        assertThat(dto.type()).isEqualTo(VenueType.MOVIE_THEATRE);
    }

    @Test
    @DisplayName("createVenue rejects duplicate names")
    void createVenue_duplicate() {
        VenueService service = new VenueService(venueRepository);
        Venue venue = Venue.builder().name("Dup").location("x").type(VenueType.OTHER).capacity(1).build();
        when(venueRepository.existsByName("Dup")).thenReturn(true);

        assertThatThrownBy(() -> service.createVenue(venue)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo("VENUE_EXISTS");
        verify(venueRepository, never()).save(any());
    }

    @Test
    @DisplayName("getVenue / getAllVenues return data or VENUE_NOT_FOUND")
    void venue_reads() {
        VenueService service = new VenueService(venueRepository);
        Venue venue = Venue.builder().id(UUID.randomUUID()).name("V").location("l").type(VenueType.STADIUM)
                .capacity(5).build();
        UUID missing = UUID.randomUUID();
        when(venueRepository.findAll()).thenReturn(List.of(venue));
        when(venueRepository.findById(venue.getId())).thenReturn(Optional.of(venue));
        when(venueRepository.findById(missing)).thenReturn(Optional.empty());

        assertThat(service.getAllVenues()).hasSize(1);
        assertThat(service.getVenue(venue.getId()).capacity()).isEqualTo(5);
        assertThatThrownBy(() -> service.getVenue(missing)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo("VENUE_NOT_FOUND");
    }

    // ---- Movies ----

    @Test
    @DisplayName("createMovie persists, rejects duplicate titles and reports MOVIE_NOT_FOUND")
    void movie_flow() {
        MovieService service = new MovieService(movieRepository);
        Movie movie = Movie.builder().title("Dune").director("Villeneuve").durationMinutes(155).build();
        when(movieRepository.existsByTitle("Dune")).thenReturn(false, true);
        when(movieRepository.save(movie)).thenAnswer(i -> {
            movie.setId(UUID.randomUUID());
            return movie;
        });

        assertThat(service.createMovie(movie).title()).isEqualTo("Dune");
        assertThatThrownBy(() -> service.createMovie(movie)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo("MOVIE_EXISTS");

        UUID missing = UUID.randomUUID();
        when(movieRepository.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getMovie(missing)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo("MOVIE_NOT_FOUND");
    }

    @Test
    @DisplayName("getAllMovies maps entities to DTOs")
    void movie_list() {
        MovieService service = new MovieService(movieRepository);
        when(movieRepository.findAll()).thenReturn(List.of(Movie.builder().id(UUID.randomUUID()).title("A").build()));

        assertThat(service.getAllMovies()).extracting("title").containsExactly("A");
    }

    // ---- Performers ----

    @Test
    @DisplayName("createPerformer persists, rejects duplicate names and reports PERFORMER_NOT_FOUND")
    void performer_flow() {
        PerformerService service = new PerformerService(performerRepository);
        Performer performer = Performer.builder().name("Band").description("rock").build();
        when(performerRepository.existsByName("Band")).thenReturn(false, true);
        when(performerRepository.save(performer)).thenAnswer(i -> {
            performer.setId(UUID.randomUUID());
            return performer;
        });

        assertThat(service.createPerformer(performer).name()).isEqualTo("Band");
        assertThatThrownBy(() -> service.createPerformer(performer)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo("PERFORMER_EXISTS");

        UUID missing = UUID.randomUUID();
        when(performerRepository.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPerformer(missing)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo("PERFORMER_NOT_FOUND");
    }

    @Test
    @DisplayName("getAllPerformers maps entities to DTOs")
    void performer_list() {
        PerformerService service = new PerformerService(performerRepository);
        when(performerRepository.findAll())
                .thenReturn(List.of(Performer.builder().id(UUID.randomUUID()).name("P").build()));

        assertThat(service.getAllPerformers()).extracting("name").containsExactly("P");
    }
}

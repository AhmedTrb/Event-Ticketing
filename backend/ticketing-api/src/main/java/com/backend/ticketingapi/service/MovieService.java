package com.backend.ticketingapi.service;

import com.backend.shared.exceptions.BusinessException;
import com.backend.ticketingapi.domain.event.Movie;
import com.backend.ticketingapi.dto.MovieDTO;
import com.backend.ticketingapi.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class MovieService {

    private final MovieRepository movieRepository;

    public MovieDTO createMovie(Movie movie) {
        if (movieRepository.existsByTitle(movie.getTitle())) {
            throw new BusinessException("MOVIE_EXISTS", "Movie with this title already exists");
        }
        return mapToDTO(movieRepository.save(movie));
    }

    @Transactional(readOnly = true)
    public List<MovieDTO> getAllMovies() {
        return movieRepository.findAll().stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public MovieDTO getMovie(UUID id) {
        return movieRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new BusinessException("MOVIE_NOT_FOUND", "Movie not found"));
    }

    private MovieDTO mapToDTO(Movie movie) {
        return new MovieDTO(
                movie.getId(),
                movie.getTitle(),
                movie.getDescription(),
                movie.getDirector(),
                movie.getReleaseDate(),
                movie.getDurationMinutes(),
                movie.getPosterUrl());
    }
}

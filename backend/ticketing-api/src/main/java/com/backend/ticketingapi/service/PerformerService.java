package com.backend.ticketingapi.service;

import com.backend.shared.exceptions.BusinessException;
import com.backend.ticketingapi.domain.event.Performer;
import com.backend.ticketingapi.dto.PerformerDTO;
import com.backend.ticketingapi.repository.PerformerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PerformerService {

    private final PerformerRepository performerRepository;

    public PerformerDTO createPerformer(Performer performer) {
        if (performerRepository.existsByName(performer.getName())) {
            throw new BusinessException("PERFORMER_EXISTS", "Performer with this name already exists");
        }
        return mapToDTO(performerRepository.save(performer));
    }

    @Transactional(readOnly = true)
    public List<PerformerDTO> getAllPerformers() {
        return performerRepository.findAll().stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public PerformerDTO getPerformer(UUID id) {
        return performerRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new BusinessException("PERFORMER_NOT_FOUND", "Performer not found"));
    }

    private PerformerDTO mapToDTO(Performer performer) {
        return new PerformerDTO(
                performer.getId(),
                performer.getName(),
                performer.getDescription(),
                performer.getImageUrl());
    }
}

package com.backend.ticketingapi.service;

import com.backend.shared.enums.UserRole;
import com.backend.shared.enums.UserStatus;
import com.backend.ticketingapi.config.KeycloakJwtAuthenticationConverter;
import com.backend.ticketingapi.domain.user.User;
import com.backend.ticketingapi.dto.UserDTO;
import com.backend.ticketingapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for authentication and user management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;

    /**
     * Get the currently authenticated user from the security context
     * 
     * @return The authenticated user
     * @throws IllegalStateException if no user is authenticated
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }

        // Get JWT from authentication
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String keycloakSub = jwt.getSubject();

        return userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new IllegalStateException(
                        "User not found in database. Please sync user first."));
    }

    /**
     * Get the current user's Keycloak subject identifier
     * 
     * @return The Keycloak sub claim
     */
    public String getCurrentUserKeycloakSub() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getSubject();
    }

    /**
     * Sync user from Keycloak JWT to local database.
     * Creates a new user if not exists, updates if exists.
     * 
     * @return The synced user
     */
    @Transactional
    public User syncUserFromKeycloak() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();

        // Extract user info from JWT
        String keycloakSub = KeycloakJwtAuthenticationConverter.getKeycloakSub(jwt);
        String email = KeycloakJwtAuthenticationConverter.getEmail(jwt);
        String givenName = KeycloakJwtAuthenticationConverter.getGivenName(jwt);
        String familyName = KeycloakJwtAuthenticationConverter.getFamilyName(jwt);

        log.info("Syncing user from Keycloak: sub={}, email={}", keycloakSub, email);

        // Check if user exists
        return userRepository.findByKeycloakSub(keycloakSub)
                .map(existingUser -> {
                    // Update existing user
                    log.info("Updating existing user: {}", existingUser.getId());
                    existingUser.setEmail(email);
                    existingUser.setFirstName(givenName);
                    existingUser.setLastName(familyName);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    // Create new user
                    log.info("Creating new user for Keycloak sub: {}", keycloakSub);
                    User newUser = User.builder()
                            .keycloakSub(keycloakSub)
                            .email(email)
                            .firstName(givenName)
                            .lastName(familyName)
                            .role(UserRole.USER)
                            .status(UserStatus.ACTIVE)
                            .build();
                    return userRepository.save(newUser);
                });
    }

    /**
     * Convert User entity to DTO
     */
    public UserDTO toDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

package com.backend.ticketingapi.repository;

import com.backend.ticketingapi.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity operations
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by Keycloak subject identifier
     * 
     * @param keycloakSub The Keycloak sub claim from JWT
     * @return Optional containing the user if found
     */
    Optional<User> findByKeycloakSub(String keycloakSub);

    /**
     * Find user by email address
     * 
     * @param email The user's email
     * @return Optional containing the user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if a user exists with the given Keycloak subject
     * 
     * @param keycloakSub The Keycloak sub claim
     * @return true if user exists
     */
    boolean existsByKeycloakSub(String keycloakSub);

    /**
     * Check if a user exists with the given email
     * 
     * @param email The user's email
     * @return true if user exists
     */
    boolean existsByEmail(String email);
}

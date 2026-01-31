package com.backend.ticketingapi.service;

import com.backend.shared.enums.UserRole;
import com.backend.shared.enums.UserStatus;
import com.backend.ticketingapi.config.KeycloakJwtAuthenticationConverter;
import com.backend.ticketingapi.domain.user.User;
import com.backend.ticketingapi.dto.LoginRequest;
import com.backend.ticketingapi.dto.RegisterRequest;
import com.backend.ticketingapi.dto.UserDTO;
import com.backend.ticketingapi.repository.UserRepository;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

/**
 * Service for authentication and user management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;

    @Value("${keycloak.server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.admin-username}")
    private String adminUsername;

    @Value("${keycloak.admin-password}")
    private String adminPassword;

    @Value("${keycloak.realm-admin-client-id}")
    private String adminClientId;

    /**
     * Login user by exchanging password for token via Keycloak
     */
    public Map<String, Object> login(LoginRequest request) {
        String tokenEndpoint = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("client_id", clientId);
        map.add("grant_type", "password");
        map.add("username", request.getEmail());
        map.add("password", request.getPassword());
        // map.add("client_secret", clientSecret); // needed if client is confidential

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenEndpoint, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Login failed for user: {}", request.getEmail(), e);
            throw new IllegalArgumentException("Invalid credentials");
        }
    }

    /**
     * Register new user via Keycloak Admin API
     */
    public void register(RegisterRequest request) {
        // 1. Get Admin Client
        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(keycloakServerUrl)
                .realm(realm) // Admin actions usually against the target realm if user has rights, or master
                .grantType("password")
                .clientId(adminClientId) // "admin-cli" usually
                .username(adminUsername)
                .password(adminPassword)
                .build();

        // 2. Prepare User Representation
        UserRepresentation user = new UserRepresentation();
        user.setEnabled(true);
        user.setUsername(request.getEmail()); // Using email as username
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmailVerified(true); // Auto-verify for simplicity

        // 3. Credentials
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.getPassword());
        credential.setTemporary(false);
        user.setCredentials(Collections.singletonList(credential));

        // 4. Create User
        UsersResource usersResource = keycloak.realm(realm).users();
        try (Response response = usersResource.create(user)) {
            if (response.getStatus() == 201) {
                String userId = CreatedResponseUtil.getCreatedId(response);
                log.info("User created in Keycloak with ID: {}", userId);

                // (Optional) We could sync to local DB here immediately,
                // but we'll let the login sync handle it or call sync explicitly.
            } else {
                log.error("Failed to create user. Status: {}", response.getStatus());
                throw new IllegalArgumentException("Failed to register user. Email might be taken.");
            }
        } catch (Exception e) {
            log.error("Error creating user", e);
            throw new RuntimeException("Registration failed", e);
        }
    }

    /**
     * Exchange Authorization Code for Token (Google / SSO Flow)
     */
    public Map<String, Object> exchangeCode(String code, String redirectUri) {
        String tokenEndpoint = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("client_id", clientId);
        map.add("grant_type", "authorization_code");
        map.add("code", code);
        map.add("redirect_uri", redirectUri);
        // map.add("client_secret", clientSecret); // Add if client is confidential

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenEndpoint, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Token exchange failed for code: {}", code, e);
            throw new IllegalArgumentException("Invalid authorization code");
        }
    }

    /**
     * Get the currently authenticated user from the security context
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();
        String keycloakSub = jwt.getSubject();

        return userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new IllegalStateException(
                        "User not found in database. Please sync user first."));
    }

    /**
     * Sync user from Keycloak JWT to local database.
     */
    @Transactional
    public User syncUserFromKeycloak() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();

        String keycloakSub = KeycloakJwtAuthenticationConverter.getKeycloakSub(jwt);
        String email = KeycloakJwtAuthenticationConverter.getEmail(jwt);
        String givenName = KeycloakJwtAuthenticationConverter.getGivenName(jwt);
        String familyName = KeycloakJwtAuthenticationConverter.getFamilyName(jwt);

        log.info("Syncing user from Keycloak: sub={}, email={}", keycloakSub, email);

        return userRepository.findByKeycloakSub(keycloakSub)
                .map(existingUser -> {
                    log.info("Updating existing user: {}", existingUser.getId());
                    existingUser.setEmail(email);
                    existingUser.setFirstName(givenName);
                    existingUser.setLastName(familyName);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
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

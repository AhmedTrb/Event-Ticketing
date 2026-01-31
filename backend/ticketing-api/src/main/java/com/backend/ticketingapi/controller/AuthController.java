package com.backend.ticketingapi.controller;



import com.backend.ticketingapi.domain.user.User;
import com.backend.ticketingapi.dto.LoginRequest;
import com.backend.ticketingapi.dto.RegisterRequest;
import com.backend.ticketingapi.dto.UserDTO;
import com.backend.ticketingapi.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication and user management endpoints
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and user management API")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Login with email and password")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    @Operation(summary = "Register", description = "Register a new user")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(201).build();
    }

    @PostMapping("/google")
    @Operation(summary = "Google Login", description = "Exchange authorization code for token")
    public ResponseEntity<Map<String, Object>> googleLogin(@RequestBody Map<String, String> payload) {
        String code = payload.get("code");
        String redirectUri = payload.get("redirectUri");
        return ResponseEntity.ok(authService.exchangeCode(code, redirectUri));
    }

    /**
     * Get current authenticated user profile
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Returns the profile of the currently authenticated user", security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<UserDTO> getCurrentUser() {
        User user = authService.getCurrentUser();
        return ResponseEntity.ok(authService.toDTO(user));
    }

    /**
     * Sync user from Keycloak to local database
     * This should be called after user authentication to ensure user exists in
     * database
     */
    @PostMapping("/sync")
    @Operation(summary = "Sync user from Keycloak", description = "Synchronizes user data from Keycloak JWT to local database. Creates user if not exists.", security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<UserDTO> syncUser() {
        User user = authService.syncUserFromKeycloak();
        return ResponseEntity.ok(authService.toDTO(user));
    }

    /**
     * Health check endpoint (public)
     */
    @GetMapping("/public/health")
    @Operation(summary = "Public health check", description = "Public endpoint to verify API is running")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Authentication API is running");
    }
}

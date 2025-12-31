package com.backend.ticketingapi.domain.user;

import com.backend.shared.enums.UserRole;
import com.backend.shared.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Principal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * User entity representing authenticated users in the ticketing system.
 * Implements UserDetails for Spring Security integration and Principal for
 * cleaner access.
 * 
 * Users are synced from Keycloak using the keycloakSub as the unique
 * identifier.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_keycloak_sub", columnList = "keycloak_sub", unique = true),
        @Index(name = "idx_email", columnList = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails, Principal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Keycloak subject identifier (sub claim from JWT)
     * This is the authoritative identifier linking this user to Keycloak
     */
    @Column(name = "keycloak_sub", nullable = false, unique = true, length = 255)
    private String keycloakSub;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ==========================================
    // UserDetails Implementation
    // ==========================================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        // Password is managed by Keycloak, not stored locally
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status == UserStatus.ACTIVE;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }

    // ==========================================
    // Principal Implementation
    // ==========================================

    @Override
    public String getName() {
        return email;
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    /**
     * Get the full name of the user
     */
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        }
        return email;
    }

    /**
     * Check if user has admin role
     */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}

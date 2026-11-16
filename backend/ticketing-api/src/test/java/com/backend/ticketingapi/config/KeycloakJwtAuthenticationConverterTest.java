package com.backend.ticketingapi.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    private Jwt.Builder jwt() {
        return Jwt.withTokenValue("token").header("alg", "none").subject("user-sub")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    }

    @Test
    @DisplayName("realm and client roles are mapped to upper-cased ROLE_ authorities")
    void mapsRealmAndClientRoles() {
        Jwt token = jwt()
                .claim("realm_access", Map.of("roles", List.of("admin", "user")))
                .claim("resource_access", Map.of("ticketing-api-client", Map.of("roles", List.of("manager"))))
                .build();

        AbstractAuthenticationToken auth = converter.convert(token);

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER", "ROLE_MANAGER");
    }

    @Test
    @DisplayName("a token without role claims yields no authorities")
    void noRoleClaims() {
        assertThat(converter.convert(jwt().claim("foo", "bar").build()).getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("resource access entries without roles are ignored")
    void resourceWithoutRoles() {
        Jwt token = jwt().claim("resource_access", Map.of("account", Map.of("other", "x"))).build();

        assertThat(converter.convert(token).getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("identity helpers expose sub, email and names")
    void identityHelpers() {
        Jwt token = jwt().claim("email", "a@b.c").claim("preferred_username", "ab")
                .claim("given_name", "Ada").claim("family_name", "Lovelace").build();

        assertThat(KeycloakJwtAuthenticationConverter.getKeycloakSub(token)).isEqualTo("user-sub");
        assertThat(KeycloakJwtAuthenticationConverter.getEmail(token)).isEqualTo("a@b.c");
        assertThat(KeycloakJwtAuthenticationConverter.getPreferredUsername(token)).isEqualTo("ab");
        assertThat(KeycloakJwtAuthenticationConverter.getGivenName(token)).isEqualTo("Ada");
        assertThat(KeycloakJwtAuthenticationConverter.getFamilyName(token)).isEqualTo("Lovelace");
    }
}

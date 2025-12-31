package com.backend.ticketingapi.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Custom JWT Authentication Converter for Keycloak tokens.
 * Extracts roles from Keycloak realm_access and resource_access claims
 * and maps them to Spring Security authorities.
 */
@Component
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String RESOURCE_ACCESS_CLAIM = "resource_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities);
    }

    /**
     * Extract authorities from Keycloak JWT token
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // Extract realm roles
        Collection<GrantedAuthority> realmRoles = extractRealmRoles(jwt);

        // Extract resource/client roles
        Collection<GrantedAuthority> resourceRoles = extractResourceRoles(jwt);

        // Combine all roles
        return java.util.stream.Stream.concat(
                realmRoles.stream(),
                resourceRoles.stream()).collect(Collectors.toList());
    }

    /**
     * Extract realm-level roles from JWT
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim(REALM_ACCESS_CLAIM);

        if (realmAccess == null || realmAccess.get(ROLES_CLAIM) == null) {
            return Collections.emptyList();
        }

        Collection<String> roles = (Collection<String>) realmAccess.get(ROLES_CLAIM);

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()))
                .collect(Collectors.toList());
    }

    /**
     * Extract client/resource-level roles from JWT
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractResourceRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaim(RESOURCE_ACCESS_CLAIM);

        if (resourceAccess == null) {
            return Collections.emptyList();
        }

        return resourceAccess.values().stream()
                .filter(resource -> resource instanceof Map)
                .flatMap(resource -> {
                    Map<String, Object> resourceMap = (Map<String, Object>) resource;
                    Collection<String> roles = (Collection<String>) resourceMap.get(ROLES_CLAIM);

                    if (roles == null) {
                        return java.util.stream.Stream.empty();
                    }

                    return roles.stream()
                            .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()));
                })
                .collect(Collectors.toList());
    }

    /**
     * Helper method to get user's Keycloak subject (sub claim)
     */
    public static String getKeycloakSub(Jwt jwt) {
        return jwt.getSubject();
    }

    /**
     * Helper method to get user's email from JWT
     */
    public static String getEmail(Jwt jwt) {
        return jwt.getClaim("email");
    }

    /**
     * Helper method to get user's preferred username
     */
    public static String getPreferredUsername(Jwt jwt) {
        return jwt.getClaim("preferred_username");
    }

    /**
     * Helper method to get user's given name
     */
    public static String getGivenName(Jwt jwt) {
        return jwt.getClaim("given_name");
    }

    /**
     * Helper method to get user's family name
     */
    public static String getFamilyName(Jwt jwt) {
        return jwt.getClaim("family_name");
    }
}

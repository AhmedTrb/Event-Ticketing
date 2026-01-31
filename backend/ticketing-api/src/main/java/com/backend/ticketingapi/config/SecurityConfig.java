package com.backend.ticketingapi.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for OAuth2 Resource Server with Keycloak JWT
 * validation
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

        private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                // Disable CSRF for stateless API
                                .csrf(csrf -> csrf.disable())

                                // Configure CORS
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                                // Stateless session management
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                // Configure authorization rules
                                .authorizeHttpRequests(auth -> auth
                                                // Public endpoints
                                                .requestMatchers("/api/auth/**").permitAll()
                                                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                                                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                                .permitAll()

                                                // WebSocket endpoints
                                                .requestMatchers("/ws/**").permitAll()

                                                // Public Read Access to Core Entities
                                                .requestMatchers(HttpMethod.GET, "/api/events/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/venues/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/movies/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/performers/**").permitAll()

                                                // Admin-only Write Access
                                                .requestMatchers(HttpMethod.POST, "/api/events/**", "/api/venues/**",
                                                                "/api/movies/**", "/api/performers/**")
                                                .hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.PUT, "/api/events/**", "/api/venues/**",
                                                                "/api/movies/**", "/api/performers/**")
                                                .hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.DELETE, "/api/events/**", "/api/venues/**",
                                                                "/api/movies/**", "/api/performers/**")
                                                .hasRole("ADMIN")

                                                // Other Admin endpoints
                                                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                                                // All other API endpoints require authentication
                                                .requestMatchers("/api/**").authenticated()

                                                // Deny all other requests
                                                .anyRequest().denyAll())

                                // Configure OAuth2 Resource Server with JWT
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt
                                                                .jwtAuthenticationConverter(
                                                                                keycloakJwtAuthenticationConverter)));

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();

                // Allow specific origins (from application.yml)
                configuration.setAllowedOrigins(Arrays.asList(
                                "http://localhost:8080",
                                "http://localhost:4200"));

                // Allow specific HTTP methods
                configuration.setAllowedMethods(Arrays.asList(
                                HttpMethod.GET.name(),
                                HttpMethod.POST.name(),
                                HttpMethod.PUT.name(),
                                HttpMethod.DELETE.name(),
                                HttpMethod.PATCH.name(),
                                HttpMethod.OPTIONS.name()));

                // Allow specific headers
                configuration.setAllowedHeaders(List.of("*"));

                // Allow credentials
                configuration.setAllowCredentials(true);

                // Max age for preflight requests
                configuration.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);

                return source;
        }
}

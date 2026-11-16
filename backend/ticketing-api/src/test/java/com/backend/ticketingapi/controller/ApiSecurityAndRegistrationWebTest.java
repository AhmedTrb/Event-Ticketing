package com.backend.ticketingapi.controller;

import com.backend.shared.exceptions.SeatAlreadyBookedException;
import com.backend.ticketingapi.config.GlobalExceptionHandler;
import com.backend.ticketingapi.config.KeycloakJwtAuthenticationConverter;
import com.backend.ticketingapi.config.SecurityConfig;
import com.backend.ticketingapi.dto.EventDTO;
import com.backend.ticketingapi.service.AuthService;
import com.backend.ticketingapi.service.EventService;
import com.backend.ticketingapi.service.RegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the documented HTTP contract: public discovery, authenticated
 * registration (202 Accepted + tracking id / 409 Conflict) and admin-only writes.
 */
@WebMvcTest(controllers = { RegistrationController.class, EventController.class, AuthController.class })
@Import({ SecurityConfig.class, KeycloakJwtAuthenticationConverter.class, GlobalExceptionHandler.class })
class ApiSecurityAndRegistrationWebTest {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private JwtDecoder jwtDecoder;
    @MockBean
    private RegistrationService registrationService;
    @MockBean
    private EventService eventService;
    @MockBean
    private AuthService authService;

    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    private String registrationBody(String seatsJson) {
        return "{\"eventId\":\"" + eventId + "\",\"seatIdentifiers\":" + seatsJson + "}";
    }

    @Test
    @DisplayName("GET /api/events is public")
    void events_publicRead() throws Exception {
        when(eventService.getUpcomingEvents(any())).thenReturn(new PageImpl<EventDTO>(List.of()));

        mvc.perform(get("/api/events")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("profile endpoints under /api/auth need a token even though login/register are public")
    void profileEndpoints_requireAuth() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/sync")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/public/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("registration endpoints require authentication (401)")
    void registration_requiresAuth() throws Exception {
        mvc.perform(post("/api/register/queue").contentType(MediaType.APPLICATION_JSON)
                .content(registrationBody("[\"A1\"]"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/register/simple").contentType(MediaType.APPLICATION_JSON)
                .content(registrationBody("[\"A1\"]"))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/register/queue -> 202 Accepted with the tracking bookingId")
    void queueRegistration_accepted() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(registrationService.registerViaQueue(userId, eventId, List.of("A1", "A2"))).thenReturn(bookingId);

        mvc.perform(post("/api/register/queue").with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(registrationBody("[\"A1\",\"A2\"]")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()));
    }

    @Test
    @DisplayName("POST /api/register/queue -> 409 Conflict when seats are taken")
    void queueRegistration_conflict() throws Exception {
        when(registrationService.registerViaQueue(eq(userId), eq(eventId), any()))
                .thenThrow(new SeatAlreadyBookedException(eventId, "A1"));

        mvc.perform(post("/api/register/queue").with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(registrationBody("[\"A1\"]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SEAT_ALREADY_BOOKED"));
    }

    @Test
    @DisplayName("POST /api/register/simple -> 200 with bookingId")
    void simpleRegistration_ok() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(registrationService.registerSimple(userId, eventId, List.of("B1"))).thenReturn(bookingId);

        mvc.perform(post("/api/register/simple").with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(registrationBody("[\"B1\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()));
    }

    @Test
    @DisplayName("registration validates the payload (empty seats / missing event -> 400)")
    void registration_validation() throws Exception {
        mvc.perform(post("/api/register/queue").with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(registrationBody("[]")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/register/queue").with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"seatIdentifiers\":[\"A1\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("creating events is ADMIN only: 401 anonymous, 403 USER, 201 ADMIN")
    void createEvent_adminOnly() throws Exception {
        Instant start = Instant.now().plus(5, ChronoUnit.DAYS);
        String body = "{\"name\":\"Gig\",\"startDate\":\"" + start + "\",\"endDate\":\""
                + start.plus(2, ChronoUnit.HOURS) + "\",\"eventType\":\"CONCERT\",\"venueId\":\""
                + UUID.randomUUID() + "\"}";

        mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/events").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());

        mvc.perform(post("/api/events").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        verify(eventService).createEvent(any());
    }
}

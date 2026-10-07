package com.rockey.hospitality.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rockey.hospitality.dto.CommonDtos.PagedResponse;
import com.rockey.hospitality.dto.EventDtos.CreateEventRequest;
import com.rockey.hospitality.dto.EventDtos.EventRegistrationResponse;
import com.rockey.hospitality.dto.EventDtos.EventResponse;
import com.rockey.hospitality.dto.EventDtos.EventSummary;
import com.rockey.hospitality.dto.EventDtos.UpdateEventRequest;
import com.rockey.hospitality.entity.Event;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.exception.ApiException.ResourceNotFoundException;
import com.rockey.hospitality.exception.GlobalExceptionHandler;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.EventService;
import com.rockey.hospitality.service.RegistrationService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    @Mock
    private EventService eventService;

    @Mock
    private RegistrationService registrationService;

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = standaloneSetup(new EventController(eventService, registrationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        validator.destroy();
    }

    @Test
    void listPassesStatusDateAndPaginationFilters() throws Exception {
        LocalDateTime from = LocalDateTime.of(2030, 10, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2030, 10, 31, 23, 59);
        when(eventService.listEvents(
                Event.Status.OPEN,
                from,
                to,
                1,
                5,
                "eventDateTime,asc"
        )).thenReturn(new PagedResponse<>(List.of(response()), 1, 5, 6, 2, true));

        mockMvc.perform(get("/api/events")
                        .param("status", "OPEN")
                        .param("dateFrom", "2030-10-01T00:00:00")
                        .param("dateTo", "2030-10-31T23:59:00")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.content[0].remainingCapacity").value(100));
    }

    @Test
    void createReturns201AndValidatesInput() throws Exception {
        when(eventService.createEvent(any(CreateEventRequest.class))).thenReturn(response());
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"x",
                                  "eventDateTime":"2030-10-10T09:00:00",
                                  "location":"x",
                                  "capacity":0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.location").exists())
                .andExpect(jsonPath("$.fieldErrors.capacity").exists());
    }

    @Test
    void getAndUpdateReturnCanonicalResponse() throws Exception {
        when(eventService.getEvent(7L)).thenReturn(response());
        mockMvc.perform(get("/api/events/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskCount").value(5));

        when(eventService.updateEvent(any(Long.class), any(UpdateEventRequest.class)))
                .thenReturn(response());
        mockMvc.perform(put("/api/events/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredCount").value(20));
    }

    @Test
    void cancelReturns200WithCancelledEventResponse() throws Exception {
        when(eventService.cancelEvent(7L)).thenReturn(response(Event.Status.CANCELLED));

        mockMvc.perform(delete("/api/events/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void registerUsesAuthenticatedUserAndReturns201() throws Exception {
        EventRegistrationResponse registration = new EventRegistrationResponse(
                31L,
                new EventSummary(
                        7L,
                        "Leadership Conference",
                        futureDate(),
                        "Ballroom A",
                        Event.Status.OPEN,
                        99
                )
        );
        when(registrationService.register(7L, 31L)).thenReturn(registration);

        mockMvc.perform(post("/api/events/7/registrations")
                        .principal(authentication(31L, User.Role.USER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(31))
                .andExpect(jsonPath("$.event.id").value(7));

        verify(registrationService).register(7L, 31L);
    }

    @Test
    void withdrawUsesAuthenticatedUserAndReturns204() throws Exception {
        mockMvc.perform(delete("/api/events/7/registrations/me")
                        .principal(authentication(31L, User.Role.USER)))
                .andExpect(status().isNoContent());

        verify(registrationService).withdraw(7L, 31L);
    }

    @Test
    void listOwnRegistrationsUsesAuthenticatedUser() throws Exception {
        when(registrationService.listOwnRegistrations(
                31L,
                0,
                20,
                "eventDateTime,asc"
        )).thenReturn(new PagedResponse<>(List.of(response()), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/events/registrations/me")
                        .principal(authentication(31L, User.Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7));
    }

    @Test
    void missingEventReturnsCanonical404() throws Exception {
        when(eventService.getEvent(99L))
                .thenThrow(new ResourceNotFoundException("Event not found with id 99."));

        mockMvc.perform(get("/api/events/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private String validCreateJson() {
        return """
                {
                  "title":"Leadership Conference",
                  "description":"One-day conference.",
                  "eventDateTime":"2030-10-10T09:00:00",
                  "location":"Ballroom A",
                  "capacity":120,
                  "initialStatus":"OPEN"
                }
                """;
    }

    private String validUpdateJson() {
        return """
                {
                  "title":"Leadership Conference",
                  "description":"One-day conference.",
                  "eventDateTime":"2030-10-10T09:00:00",
                  "location":"Ballroom A",
                  "capacity":120,
                  "status":"OPEN"
                }
                """;
    }

    private UsernamePasswordAuthenticationToken authentication(Long id, User.Role role) {
        User user = new User("Test User", role.name().toLowerCase() + "@example.test", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", role);
        RockeyUserPrincipal principal = new RockeyUserPrincipal(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    private EventResponse response() {
        return response(Event.Status.OPEN);
    }

    private EventResponse response(Event.Status status) {
        return new EventResponse(
                7L,
                "Leadership Conference",
                "One-day conference.",
                futureDate(),
                "Ballroom A",
                120,
                status,
                20,
                100,
                5,
                3,
                LocalDateTime.of(2026, 10, 3, 9, 0),
                LocalDateTime.of(2026, 10, 3, 10, 0)
        );
    }

    private LocalDateTime futureDate() {
        return LocalDateTime.of(2030, 10, 10, 9, 0);
    }
}

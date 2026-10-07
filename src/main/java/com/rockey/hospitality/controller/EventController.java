package com.rockey.hospitality.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.rockey.hospitality.exception.ApiError;

import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.dto.event.CreateEventRequest;
import com.rockey.hospitality.dto.event.EventRegistrationResponse;
import com.rockey.hospitality.dto.event.EventResponse;
import com.rockey.hospitality.dto.event.UpdateEventRequest;
import com.rockey.hospitality.entity.EventStatus;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.EventService;
import com.rockey.hospitality.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Binds Event HTTP requests and delegates business operations to its service layer.
 * Response DTOs and HTTP statuses are kept separate from JPA entities.
 */
// Registers a web controller whose mapped return values are written as response bodies, normally JSON.
@RestController
// Groups this controller's routes under /api/events.
@RequestMapping("/api/events")
public class EventController {

    /**
     * Injected EventService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final EventService eventService;
    /**
     * Injected RegistrationService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final RegistrationService registrationService;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public EventController(
            EventService eventService,
            RegistrationService registrationService
    ) {
        this.eventService = eventService;
        this.registrationService = registrationService;
    }

    /**
     * List eligible events.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "List eligible events", description = "Access: USER, STAFF, ADMIN. Canonical operation: GET /api/events.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to the controller's base route.
    @GetMapping
    public PagedResponse<EventResponse> listEvents(
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) EventStatus status,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false)
            // Parses the query value as an ISO date-time into LocalDateTime; it does not assign a timezone.
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false)
            // Parses the query value as an ISO date-time into LocalDateTime; it does not assign a timezone.
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            // Binds this query parameter, defaulting to 0 when omitted.
            @RequestParam(defaultValue = "0") int page,
            // Binds this query parameter, defaulting to 20 when omitted.
            @RequestParam(defaultValue = "20") int size,
            // Binds this query parameter, defaulting to eventDateTime,asc when omitted.
            @RequestParam(defaultValue = "eventDateTime,asc") String sort
    ) {
        return eventService.listEvents(status, dateFrom, dateTo, page, size, sort);
    }

    /**
     * Create event.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity sets 201 and returns the created DTO.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Create event", description = "Access: ADMIN. Canonical operation: POST /api/events.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP POST to the controller's base route.
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            // Validates the bound request DTO before the controller delegates to its service.
            @Valid
            // Deserializes the request JSON into this writable DTO.
            @RequestBody CreateEventRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventService.createEvent(request));
    }

    /**
     * Event detail/capacity/preparation.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Event detail/capacity/preparation", description = "Access: USER, STAFF, ADMIN. Canonical operation: GET /api/events/{eventId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to this /{eventId} suffix.
    @GetMapping("/{eventId}")
    public EventResponse getEvent(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long eventId) {
        return eventService.getEvent(eventId);
    }

    /**
     * Update event.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Update event", description = "Access: ADMIN. Canonical operation: PUT /api/events/{eventId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP PUT to this /{eventId} suffix.
    @PutMapping("/{eventId}")
    public EventResponse updateEvent(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long eventId,
            // Validates the bound request DTO before the controller delegates to its service.
            @Valid
            // Deserializes the request JSON into this writable DTO.
            @RequestBody UpdateEventRequest request
    ) {
        return eventService.updateEvent(eventId, request);
    }

    /**
     * Cancel eligible event without hard deletion.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Cancel eligible event without hard deletion", description = "Access: ADMIN. Canonical operation: DELETE /api/events/{eventId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP DELETE to this /{eventId} suffix.
    @DeleteMapping("/{eventId}")
    public EventResponse cancelEvent(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long eventId) {
        return eventService.cancelEvent(eventId);
    }

    /**
     * Register current USER.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity sets 201 and returns the created DTO.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Register current USER", description = "Access: USER. Canonical operation: POST /api/events/{eventId}/registrations.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP POST to this /{eventId}/registrations suffix.
    @PostMapping("/{eventId}/registrations")
    public ResponseEntity<EventRegistrationResponse> register(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long eventId,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.register(eventId, principal.getId()));
    }

    /**
     * Withdraw current USER.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity returns 204 with no body after the service finishes.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Withdraw current USER", description = "Access: USER. Canonical operation: DELETE /api/events/{eventId}/registrations/me.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content", content = @Content),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP DELETE to this /{eventId}/registrations/me suffix.
    @DeleteMapping("/{eventId}/registrations/me")
    public ResponseEntity<Void> withdraw(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long eventId,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        registrationService.withdraw(eventId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * List own registrations.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "List own registrations", description = "Access: USER. Canonical operation: GET /api/events/registrations/me.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to this /registrations/me suffix.
    @GetMapping("/registrations/me")
    public PagedResponse<EventResponse> listOwnRegistrations(
            // Binds this query parameter, defaulting to 0 when omitted.
            @RequestParam(defaultValue = "0") int page,
            // Binds this query parameter, defaulting to 20 when omitted.
            @RequestParam(defaultValue = "20") int size,
            // Binds this query parameter, defaulting to eventDateTime,asc when omitted.
            @RequestParam(defaultValue = "eventDateTime,asc") String sort,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        return registrationService.listOwnRegistrations(
                principal.getId(),
                page,
                size,
                sort
        );
    }
}

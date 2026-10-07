package com.rockey.hospitality.controller;

import com.rockey.hospitality.dto.common.PageCriteria;
import com.rockey.hospitality.dto.alert.AlertSearchCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.rockey.hospitality.exception.ApiError;

import com.rockey.hospitality.dto.alert.AlertResponse;
import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.entity.AlertStatus;
import com.rockey.hospitality.entity.AlertType;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.AlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Binds Alert HTTP requests and delegates business operations to its service layer.
 * Response DTOs and HTTP statuses are kept separate from JPA entities.
 */
// Registers a web controller whose mapped return values are written as response bodies, normally JSON.
@RestController
// Groups this controller's routes under /api/alerts.
@RequestMapping("/api/alerts")
public class AlertController {

    /**
     * Injected AlertService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final AlertService alertService;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public AlertController(AlertService alertService) { this.alertService = alertService; }

    /**
     * Own alerts or ADMIN oversight.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Own alerts or ADMIN oversight", description = "Access: STAFF own, ADMIN. Canonical operation: GET /api/alerts.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to the controller's base route.
    @GetMapping
    public PagedResponse<AlertResponse> listAlerts(
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) Long employeeId,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) AlertType type,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) AlertStatus status,
            // Binds this query parameter, defaulting to 0 when omitted.
            @RequestParam(defaultValue = "0") int page,
            // Binds this query parameter, defaulting to 20 when omitted.
            @RequestParam(defaultValue = "20") int size,
            // Binds this query parameter, defaulting to createdAt,desc when omitted.
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        return alertService.listAlerts(new AlertSearchCriteria(employeeId, type, status), new PageCriteria(page, size, sort), principal.getId(), principal.getRole());
    }

    /**
     * Alert detail.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Alert detail", description = "Access: Recipient STAFF, ADMIN. Canonical operation: GET /api/alerts/{alertId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to this /{alertId} suffix.
    @GetMapping("/{alertId}")
    public AlertResponse getAlert(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long alertId,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        return alertService.getAlert(alertId, principal.getId(), principal.getRole());
    }

    /**
     * Mark own alert READ.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Mark own alert READ", description = "Access: Recipient STAFF; ADMIN for own alert. Canonical operation: PUT /api/alerts/{alertId}/read.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP PUT to this /{alertId}/read suffix.
    @PutMapping("/{alertId}/read")
    public AlertResponse markRead(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long alertId,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        return alertService.markRead(alertId, principal.getId(), principal.getRole());
    }

    /**
     * Resolve/dismiss alert without physical deletion.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity returns 204 with no body after the service finishes.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Resolve/dismiss alert without physical deletion", description = "Access: Recipient STAFF, ADMIN. Canonical operation: DELETE /api/alerts/{alertId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content", content = @Content),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP DELETE to this /{alertId} suffix.
    @DeleteMapping("/{alertId}")
    public ResponseEntity<Void> resolveAlert(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long alertId,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        alertService.resolveAlert(alertId, principal.getId(), principal.getRole());
        return ResponseEntity.noContent().build();
    }
}

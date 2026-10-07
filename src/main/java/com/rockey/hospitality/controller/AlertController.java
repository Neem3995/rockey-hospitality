package com.rockey.hospitality.controller;

import com.rockey.hospitality.dto.AlertDtos.AlertResponse;
import com.rockey.hospitality.dto.AlertDtos.AlertSearchCriteria;
import com.rockey.hospitality.dto.CommonDtos.ApiError;
import com.rockey.hospitality.dto.CommonDtos.PageCriteria;
import com.rockey.hospitality.dto.CommonDtos.PagedResponse;
import com.rockey.hospitality.entity.Alert;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
 * STUDY NOTE: A Controller is the API entry point for HTTP requests from React or another client.
 * Here, @RestController returns response data, normally JSON; @RequestMapping sets the shared /api/alerts URL.
 * AlertController handles own/authorized alert reads and history-preserving resolution and delegates
 * business rules to AlertService.
 * DTOs describe input/output; Spring Security and service checks, not hidden frontend buttons, enforce
 * permissions.
 */
@RestController
// Groups this controller's routes under /api/alerts.
@RequestMapping("/api/alerts")
public class AlertController {

    // HTTP/annotation study key:
    // @GetMapping handles HTTP GET reads; its path is appended to the controller's base URL.
    // @PutMapping handles HTTP PUT updates, including marking an Alert as READ.
    // @DeleteMapping handles DELETE requests; service rules may deactivate, cancel, resolve or withdraw rather
    // than erase rows.
    // @PathVariable reads an identifier directly from the URL path.
    // @RequestParam reads a query-string value; required=false makes it optional and defaultValue supplies an
    // omitted value.
    // @AuthenticationPrincipal supplies the identity established by Spring Security, not a client-chosen User
    // ID.
    // @Operation and @ApiResponses document the operation and its outcomes; they do not authorize or validate
    // requests.
    // @Content/@Schema describe documented bodies/types, not runtime validation or security.

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
    @Operation(summary = "Own alerts or ADMIN oversight", description = "Access: STAFF own, ADMIN. Canonical operation: GET /api/alerts.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public PagedResponse<AlertResponse> listAlerts(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Alert.Type type,
            @RequestParam(required = false) Alert.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        return alertService.listAlerts(new AlertSearchCriteria(employeeId, type, status), new PageCriteria(page, size, sort), principal.getId(), principal.getRole());
    }

    /**
     * Alert detail.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Alert detail", description = "Access: Recipient STAFF, ADMIN. Canonical operation: GET /api/alerts/{alertId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{alertId}")
    public AlertResponse getAlert(
            @PathVariable Long alertId,
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        return alertService.getAlert(alertId, principal.getId(), principal.getRole());
    }

    /**
     * Mark own alert READ.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Mark own alert READ", description = "Access: Recipient STAFF; ADMIN for own alert. Canonical operation: PUT /api/alerts/{alertId}/read.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{alertId}/read")
    public AlertResponse markRead(
            @PathVariable Long alertId,
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        return alertService.markRead(alertId, principal.getId(), principal.getRole());
    }

    /**
     * Resolve/dismiss alert without physical deletion.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity returns 204 with no body after the service finishes.
     */
    @Operation(summary = "Resolve/dismiss alert without physical deletion", description = "Access: Recipient STAFF, ADMIN. Canonical operation: DELETE /api/alerts/{alertId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content", content = @Content),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{alertId}")
    public ResponseEntity<Void> resolveAlert(
            @PathVariable Long alertId,
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        alertService.resolveAlert(alertId, principal.getId(), principal.getRole());
        return ResponseEntity.noContent().build();
    }
}

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

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) { this.alertService = alertService; }

    @Operation(summary = "Own alerts or ADMIN oversight", description = "Access: STAFF own, ADMIN. Canonical operation: GET /api/alerts.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public PagedResponse<AlertResponse> listAlerts(@RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) AlertType type, @RequestParam(required = false) AlertStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        return alertService.listAlerts(new AlertSearchCriteria(employeeId, type, status), new PageCriteria(page, size, sort), principal.getId(), principal.getRole());
    }

    @Operation(summary = "Alert detail", description = "Access: Recipient STAFF, ADMIN. Canonical operation: GET /api/alerts/{alertId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{alertId}")
    public AlertResponse getAlert(@PathVariable Long alertId, @AuthenticationPrincipal RockeyUserPrincipal principal) {
        return alertService.getAlert(alertId, principal.getId(), principal.getRole());
    }

    @Operation(summary = "Mark own alert READ", description = "Access: Recipient STAFF; ADMIN for own alert. Canonical operation: PUT /api/alerts/{alertId}/read.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{alertId}/read")
    public AlertResponse markRead(@PathVariable Long alertId, @AuthenticationPrincipal RockeyUserPrincipal principal) {
        return alertService.markRead(alertId, principal.getId(), principal.getRole());
    }

    @Operation(summary = "Resolve/dismiss alert without physical deletion", description = "Access: Recipient STAFF, ADMIN. Canonical operation: DELETE /api/alerts/{alertId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content", content = @Content),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{alertId}")
    public ResponseEntity<Void> resolveAlert(@PathVariable Long alertId, @AuthenticationPrincipal RockeyUserPrincipal principal) {
        alertService.resolveAlert(alertId, principal.getId(), principal.getRole());
        return ResponseEntity.noContent().build();
    }
}

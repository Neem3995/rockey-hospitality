package com.rockey.hospitality.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.rockey.hospitality.exception.ApiError;

import com.rockey.hospitality.dto.analytics.*;
import com.rockey.hospitality.exception.BadRequestException;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.AnalyticsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Binds Analytics HTTP requests and delegates business operations to its service layer.
 * Response DTOs and HTTP statuses are kept separate from JPA entities.
 */
// Registers a web controller whose mapped return values are written as response bodies, normally JSON.
@RestController
// Groups this controller's routes under /api/analytics.
@RequestMapping("/api/analytics")
public class AnalyticsController {
    /**
     * Canonical query-parameter name shared by allowed-name checks and numeric parsing.
     */
    private static final String DEPARTMENT_ID = "departmentId";
    /**
     * Injected AnalyticsService; the controller binds HTTP input while the service owns scope and counts.
     */
    private final AnalyticsService service;
    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public AnalyticsController(AnalyticsService service) { this.service = service; }

    /**
     * Role-specific dashboard.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Role-specific dashboard", description = "Access: USER, STAFF, ADMIN. Canonical operation: GET /api/analytics/dashboard.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to this /dashboard suffix.
    @GetMapping("/dashboard")
    public DashboardResponse dashboard(
            // Documents or hides an OpenAPI parameter; nested @Schema documents its type and limits without performing runtime validation.
            @Parameter(hidden = true)
            // Binds query parameters for this controller; the service or explicit validator checks their allowed values.
            @RequestParam MultiValueMap<String, String> params,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        validateParams(params, Set.of());
        return service.dashboard(principal.getId(), principal.getRole());
    }

    /**
     * Room readiness/turnover.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Room readiness/turnover", description = "Access: ADMIN. Canonical operation: GET /api/analytics/rooms.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to this /rooms suffix.
    @GetMapping("/rooms")
    // Documents or hides an OpenAPI parameter; nested @Schema documents its type and limits without performing runtime validation.
    @Parameter(name = "floor", in = ParameterIn.QUERY, description = "Optional floor, 1–99; omitted includes all floors.",
            schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "99"))
    public RoomAnalyticsResponse rooms(
            // Documents or hides an OpenAPI parameter; nested @Schema documents its type and limits without performing runtime validation.
            @Parameter(hidden = true)
            // Binds query parameters for this controller; the service or explicit validator checks their allowed values.
            @RequestParam MultiValueMap<String, String> params,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        validateParams(params, Set.of("floor"));
        return service.rooms(integer(params, "floor", null), principal.getRole());
    }

    /**
     * Task completion/overdue.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Task completion/overdue", description = "Access: ADMIN. Canonical operation: GET /api/analytics/tasks.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to this /tasks suffix.
    @GetMapping("/tasks")
    // Documents or hides an OpenAPI parameter; nested @Schema documents its type and limits without performing runtime validation.
    @Parameter(name = DEPARTMENT_ID, in = ParameterIn.QUERY, description = "Positive Department ID; unknown ID returns zero counts.",
            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    public TaskAnalyticsResponse tasks(
            // Documents or hides an OpenAPI parameter; nested @Schema documents its type and limits without performing runtime validation.
            @Parameter(hidden = true)
            // Binds query parameters for this controller; the service or explicit validator checks their allowed values.
            @RequestParam MultiValueMap<String, String> params,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        validateParams(params, Set.of(DEPARTMENT_ID));
        return service.tasks(departmentId(params), principal.getRole());
    }

    /**
     * Department workload.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Department workload", description = "Access: ADMIN. Canonical operation: GET /api/analytics/departments.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to this /departments suffix.
    @GetMapping("/departments")
    // Groups documented query parameters; nested @Parameter and @Schema describe names, types, limits, and defaults.
    @Parameters({
        @Parameter(name = DEPARTMENT_ID, in = ParameterIn.QUERY, description = "Optional positive Department ID; unknown ID returns 404.", schema = @Schema(type = "integer", format = "int64", minimum = "1")),
        @Parameter(name = "page", in = ParameterIn.QUERY, description = "Zero-based page; fixed Department ID ascending order.", schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0")),
        @Parameter(name = "size", in = ParameterIn.QUERY, schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
    })
    public DepartmentAnalyticsResponse departments(
            // Documents or hides an OpenAPI parameter; nested @Schema documents its type and limits without performing runtime validation.
            @Parameter(hidden = true)
            // Binds query parameters for this controller; the service or explicit validator checks their allowed values.
            @RequestParam MultiValueMap<String, String> params,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        validateParams(params, Set.of(DEPARTMENT_ID, "page", "size"));
        return service.departments(departmentId(params), integer(params, "page", 0),
                integer(params, "size", 20), principal.getRole());
    }

    /**
     * Inventory thresholds and global event registration/preparation.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Inventory thresholds and global event registration/preparation", description = "Access: ADMIN. Canonical operation: GET /api/analytics/inventory-events.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to this /inventory-events suffix.
    @GetMapping("/inventory-events")
    // Documents or hides an OpenAPI parameter; nested @Schema documents its type and limits without performing runtime validation.
    @Parameter(name = DEPARTMENT_ID, in = ParameterIn.QUERY, description = "Positive Department ID filters Inventory only; Events remain global. No date filters.",
            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    public OperationsAnalyticsResponse operations(
            // Documents or hides an OpenAPI parameter; nested @Schema documents its type and limits without performing runtime validation.
            @Parameter(hidden = true)
            // Binds query parameters for this controller; the service or explicit validator checks their allowed values.
            @RequestParam MultiValueMap<String, String> params,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        validateParams(params, Set.of(DEPARTMENT_ID));
        return service.operations(departmentId(params), principal.getRole());
    }

    /**
     * Rejects unknown, repeated, null, or blank analytics query parameters before service execution.
     */
    private void validateParams(MultiValueMap<String, String> params, Set<String> allowed) {
        for (var entry : params.entrySet()) {
            List<String> values = entry.getValue();
            if (!allowed.contains(entry.getKey()) || values.size() != 1
                    || values.get(0) == null || values.get(0).isBlank()) {
                throw new BadRequestException("Unsupported, repeated, or empty analytics query parameter.");
            }
        }
    }

    /**
     * Parses an optional integer parameter or returns its documented default, translating malformed input into 400.
     */
    private Integer integer(MultiValueMap<String, String> params, String name, Integer fallback) {
        String value = params.getFirst(name);
        if (value == null) return fallback;
        try { return Integer.valueOf(value); }
        catch (NumberFormatException exception) { throw new BadRequestException("Analytics parameter must be an integer."); }
    }

    /**
     * Parses the optional Department ID as a long, reporting malformed input as 400; the service checks positivity.
     */
    private Long departmentId(MultiValueMap<String, String> params) {
        String value = params.getFirst(DEPARTMENT_ID);
        if (value == null) return null;
        try { return Long.valueOf(value); }
        catch (NumberFormatException exception) { throw new BadRequestException("Department filter must be an integer."); }
    }
}

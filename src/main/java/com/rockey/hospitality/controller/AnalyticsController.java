package com.rockey.hospitality.controller;

import com.rockey.hospitality.dto.AnalyticsDtos.*;
import com.rockey.hospitality.dto.CommonDtos.ApiError;
import com.rockey.hospitality.exception.ApiException.BadRequestException;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * STUDY NOTE: A Controller is the API entry point for HTTP requests from React or another client.
 * Here, @RestController returns response data, normally JSON; @RequestMapping sets the shared /api/analytics URL.
 * AnalyticsController handles read-only role-scoped counts and delegates business rules to
 * AnalyticsService.
 * DTOs describe input/output; Spring Security and service checks, not hidden frontend buttons, enforce
 * permissions.
 */
@RestController
// Groups this controller's routes under /api/analytics.
@RequestMapping("/api/analytics")
public class AnalyticsController {

    // HTTP/annotation study key:
    // @GetMapping handles HTTP GET reads; its path is appended to the controller's base URL.
    // @RequestParam reads a query-string value; required=false makes it optional and defaultValue supplies an
    // omitted value.
    // @AuthenticationPrincipal supplies the identity established by Spring Security, not a client-chosen User
    // ID.
    // @Operation and @ApiResponses document the operation and its outcomes; they do not authorize or validate
    // requests.
    // @Parameters/@Parameter describe query/path inputs in OpenAPI; documentation limits are not runtime
    // validation.
    // @Content/@Schema describe documented bodies/types, not runtime validation or security.
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
    @Operation(summary = "Role-specific dashboard", description = "Access: USER, STAFF, ADMIN. Canonical operation: GET /api/analytics/dashboard.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/dashboard")
    public DashboardResponse dashboard(
            @Parameter(hidden = true)
            @RequestParam MultiValueMap<String, String> params,
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        validateParams(params, Set.of());
        return service.dashboard(principal.getId(), principal.getRole());
    }

    /**
     * Room readiness/turnover.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Room readiness/turnover", description = "Access: ADMIN. Canonical operation: GET /api/analytics/rooms.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/rooms")
    @Parameter(name = "floor", in = ParameterIn.QUERY, description = "Optional floor, 1–99; omitted includes all floors.",
            schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "99"))
    public RoomAnalyticsResponse rooms(
            @Parameter(hidden = true)
            @RequestParam MultiValueMap<String, String> params,
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        validateParams(params, Set.of("floor"));
        return service.rooms(integer(params, "floor", null), principal.getRole());
    }

    /**
     * Task completion/overdue.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Task completion/overdue", description = "Access: ADMIN. Canonical operation: GET /api/analytics/tasks.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/tasks")
    @Parameter(name = DEPARTMENT_ID, in = ParameterIn.QUERY, description = "Positive Department ID; unknown ID returns zero counts.",
            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    public TaskAnalyticsResponse tasks(
            @Parameter(hidden = true)
            @RequestParam MultiValueMap<String, String> params,
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        validateParams(params, Set.of(DEPARTMENT_ID));
        return service.tasks(departmentId(params), principal.getRole());
    }

    /**
     * Department workload.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Department workload", description = "Access: ADMIN. Canonical operation: GET /api/analytics/departments.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/departments")
    // Groups documented query parameters; nested @Parameter and @Schema describe names, types, limits, and defaults.
    @Parameters({
        @Parameter(name = DEPARTMENT_ID, in = ParameterIn.QUERY, description = "Optional positive Department ID; unknown ID returns 404.", schema = @Schema(type = "integer", format = "int64", minimum = "1")),
        @Parameter(name = "page", in = ParameterIn.QUERY, description = "Zero-based page; fixed Department ID ascending order.", schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0")),
        @Parameter(name = "size", in = ParameterIn.QUERY, schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
    })
    public DepartmentAnalyticsResponse departments(
            @Parameter(hidden = true)
            @RequestParam MultiValueMap<String, String> params,
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        validateParams(params, Set.of(DEPARTMENT_ID, "page", "size"));
        return service.departments(departmentId(params), integer(params, "page", 0),
                integer(params, "size", 20), principal.getRole());
    }

    /**
     * Inventory thresholds and global event registration/preparation.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Inventory thresholds and global event registration/preparation", description = "Access: ADMIN. Canonical operation: GET /api/analytics/inventory-events.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/inventory-events")
    @Parameter(name = DEPARTMENT_ID, in = ParameterIn.QUERY, description = "Positive Department ID filters Inventory only; Events remain global. No date filters.",
            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    public OperationsAnalyticsResponse operations(
            @Parameter(hidden = true)
            @RequestParam MultiValueMap<String, String> params,
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

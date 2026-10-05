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

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private static final String DEPARTMENT_ID = "departmentId";
    private final AnalyticsService service;
    public AnalyticsController(AnalyticsService service) { this.service = service; }

    @Operation(summary = "Role-specific dashboard", description = "Access: USER, STAFF, ADMIN. Canonical operation: GET /api/analytics/dashboard.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params,
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        validateParams(params, Set.of());
        return service.dashboard(principal.getId(), principal.getRole());
    }

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
    public RoomAnalyticsResponse rooms(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params,
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        validateParams(params, Set.of("floor"));
        return service.rooms(integer(params, "floor", null), principal.getRole());
    }

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
    public TaskAnalyticsResponse tasks(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params,
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        validateParams(params, Set.of(DEPARTMENT_ID));
        return service.tasks(departmentId(params), principal.getRole());
    }

    @Operation(summary = "Department workload", description = "Access: ADMIN. Canonical operation: GET /api/analytics/departments.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/departments")
    @Parameters({
        @Parameter(name = DEPARTMENT_ID, in = ParameterIn.QUERY, description = "Optional positive Department ID; unknown ID returns 404.", schema = @Schema(type = "integer", format = "int64", minimum = "1")),
        @Parameter(name = "page", in = ParameterIn.QUERY, description = "Zero-based page; fixed Department ID ascending order.", schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0")),
        @Parameter(name = "size", in = ParameterIn.QUERY, schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
    })
    public DepartmentAnalyticsResponse departments(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params,
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        validateParams(params, Set.of(DEPARTMENT_ID, "page", "size"));
        return service.departments(departmentId(params), integer(params, "page", 0),
                integer(params, "size", 20), principal.getRole());
    }

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
    public OperationsAnalyticsResponse operations(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params,
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        validateParams(params, Set.of(DEPARTMENT_ID));
        return service.operations(departmentId(params), principal.getRole());
    }

    private void validateParams(MultiValueMap<String, String> params, Set<String> allowed) {
        for (var entry : params.entrySet()) {
            List<String> values = entry.getValue();
            if (!allowed.contains(entry.getKey()) || values.size() != 1
                    || values.get(0) == null || values.get(0).isBlank()) {
                throw new BadRequestException("Unsupported, repeated, or empty analytics query parameter.");
            }
        }
    }

    private Integer integer(MultiValueMap<String, String> params, String name, Integer fallback) {
        String value = params.getFirst(name);
        if (value == null) return fallback;
        try { return Integer.valueOf(value); }
        catch (NumberFormatException exception) { throw new BadRequestException("Analytics parameter must be an integer."); }
    }

    private Long departmentId(MultiValueMap<String, String> params) {
        String value = params.getFirst(DEPARTMENT_ID);
        if (value == null) return null;
        try { return Long.valueOf(value); }
        catch (NumberFormatException exception) { throw new BadRequestException("Department filter must be an integer."); }
    }
}

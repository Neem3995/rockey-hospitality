package com.rockey.hospitality.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.rockey.hospitality.exception.ApiError;

import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.dto.employee.CreateEmployeeRequest;
import com.rockey.hospitality.dto.employee.EmployeeResponse;
import com.rockey.hospitality.dto.employee.UpdateEmployeeRequest;
import com.rockey.hospitality.entity.EmployeeStatus;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.EmployeeService;
import jakarta.validation.Valid;
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

/**
 * Binds Employee HTTP requests and delegates business operations to its service layer.
 * Response DTOs and HTTP statuses are kept separate from JPA entities.
 */
// Registers a web controller whose mapped return values are written as response bodies, normally JSON.
@RestController
// Groups this controller's routes under /api/employees.
@RequestMapping("/api/employees")
public class EmployeeController {

    /**
     * Injected EmployeeService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final EmployeeService employeeService;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /**
     * Paginated employee list.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Paginated employee list", description = "Access: ADMIN. Canonical operation: GET /api/employees.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to the controller's base route.
    @GetMapping
    public PagedResponse<EmployeeResponse> listEmployees(
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) Long departmentId,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) EmployeeStatus status,
            // Binds this query parameter, defaulting to 0 when omitted.
            @RequestParam(defaultValue = "0") int page,
            // Binds this query parameter, defaulting to 20 when omitted.
            @RequestParam(defaultValue = "20") int size,
            // Binds this query parameter, defaulting to name,asc when omitted.
            @RequestParam(defaultValue = "name,asc") String sort
    ) {
        return employeeService.listEmployees(
                departmentId,
                status,
                page,
                size,
                sort
        );
    }

    /**
     * Create employee and optional linked login.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity sets 201 and returns the created DTO.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Create employee and optional linked login", description = "Access: ADMIN. Canonical operation: POST /api/employees.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP POST to the controller's base route.
    @PostMapping
    public ResponseEntity<EmployeeResponse> createEmployee(
            // Validates the bound request DTO before the controller delegates to its service.
            @Valid
            // Deserializes the request JSON into this writable DTO.
            @RequestBody CreateEmployeeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeService.createEmployee(request));
    }

    /**
     * Employee detail.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Employee detail", description = "Access: ADMIN; self STAFF. Canonical operation: GET /api/employees/{employeeId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to this /{employeeId} suffix.
    @GetMapping("/{employeeId}")
    public EmployeeResponse getEmployee(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long employeeId,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        // Pass the authenticated identity to the service; a requested employee ID is not authority.
        return employeeService.getEmployee(
                employeeId,
                principal.getId(),
                principal.getRole()
        );
    }

    /**
     * Update profile/department/job/status.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Update profile/department/job/status", description = "Access: ADMIN. Canonical operation: PUT /api/employees/{employeeId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP PUT to this /{employeeId} suffix.
    @PutMapping("/{employeeId}")
    public EmployeeResponse updateEmployee(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long employeeId,
            // Validates the bound request DTO before the controller delegates to its service.
            @Valid
            // Deserializes the request JSON into this writable DTO.
            @RequestBody UpdateEmployeeRequest request
    ) {
        return employeeService.updateEmployee(employeeId, request);
    }

    /**
     * Deactivate employee/account.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity returns 204 with no body after the service finishes.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Deactivate employee/account", description = "Access: ADMIN. Canonical operation: DELETE /api/employees/{employeeId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content", content = @Content),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP DELETE to this /{employeeId} suffix.
    @DeleteMapping("/{employeeId}")
    public ResponseEntity<Void> deactivateEmployee(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long employeeId) {
        employeeService.deactivateEmployee(employeeId);
        return ResponseEntity.noContent().build();
    }
}

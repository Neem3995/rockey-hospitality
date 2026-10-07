package com.rockey.hospitality.controller;

import com.rockey.hospitality.dto.CommonDtos.ApiError;
import com.rockey.hospitality.dto.CommonDtos.PagedResponse;
import com.rockey.hospitality.dto.EmployeeDtos.CreateEmployeeRequest;
import com.rockey.hospitality.dto.EmployeeDtos.EmployeeResponse;
import com.rockey.hospitality.dto.EmployeeDtos.UpdateEmployeeRequest;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
 * STUDY NOTE: A Controller is the API entry point for HTTP requests from React or another client.
 * Here, @RestController returns response data, normally JSON; @RequestMapping sets the shared /api/employees URL.
 * EmployeeController handles employee profiles and optional internal login provisioning and delegates
 * business rules to EmployeeService.
 * DTOs describe input/output; Spring Security and service checks, not hidden frontend buttons, enforce
 * permissions.
 */
@RestController
// Groups this controller's routes under /api/employees.
@RequestMapping("/api/employees")
public class EmployeeController {

    // HTTP/annotation study key:
    // @GetMapping handles HTTP GET reads; its path is appended to the controller's base URL.
    // @PostMapping handles HTTP POST creation/actions, here creating an Employee profile and optional login.
    // @PutMapping handles HTTP PUT updates, here replacing an Employee's profile fields.
    // @DeleteMapping handles DELETE requests; service rules may deactivate, cancel, resolve or withdraw rather
    // than erase rows.
    // @RequestBody reads request JSON into the declared DTO.
    // @PathVariable reads an identifier directly from the URL path.
    // @RequestParam reads a query-string value; required=false makes it optional and defaultValue supplies an
    // omitted value.
    // @Valid runs the DTO's Bean Validation checks before controller business delegation.
    // @AuthenticationPrincipal supplies the identity established by Spring Security, not a client-chosen User
    // ID.
    // @Operation and @ApiResponses document the operation and its outcomes; they do not authorize or validate
    // requests.
    // @Content/@Schema describe documented bodies/types, not runtime validation or security.

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
    @Operation(summary = "Paginated employee list", description = "Access: ADMIN. Canonical operation: GET /api/employees.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public PagedResponse<EmployeeResponse> listEmployees(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Employee.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
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
    @Operation(summary = "Create employee and optional linked login", description = "Access: ADMIN. Canonical operation: POST /api/employees.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<EmployeeResponse> createEmployee(
            @Valid
            @RequestBody CreateEmployeeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeService.createEmployee(request));
    }

    /**
     * Employee detail.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Employee detail", description = "Access: ADMIN; self STAFF. Canonical operation: GET /api/employees/{employeeId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{employeeId}")
    public EmployeeResponse getEmployee(
            @PathVariable Long employeeId,
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
    @Operation(summary = "Update profile/department/job/status", description = "Access: ADMIN. Canonical operation: PUT /api/employees/{employeeId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{employeeId}")
    public EmployeeResponse updateEmployee(
            @PathVariable Long employeeId,
            @Valid
            @RequestBody UpdateEmployeeRequest request
    ) {
        return employeeService.updateEmployee(employeeId, request);
    }

    /**
     * Deactivate employee/account.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity returns 204 with no body after the service finishes.
     */
    @Operation(summary = "Deactivate employee/account", description = "Access: ADMIN. Canonical operation: DELETE /api/employees/{employeeId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content", content = @Content),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{employeeId}")
    public ResponseEntity<Void> deactivateEmployee(
            @PathVariable Long employeeId) {
        employeeService.deactivateEmployee(employeeId);
        return ResponseEntity.noContent().build();
    }
}

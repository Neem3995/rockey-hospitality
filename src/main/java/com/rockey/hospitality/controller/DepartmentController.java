package com.rockey.hospitality.controller;

import com.rockey.hospitality.dto.CommonDtos.ApiError;
import com.rockey.hospitality.dto.DepartmentDtos.CreateDepartmentRequest;
import com.rockey.hospitality.dto.DepartmentDtos.DepartmentResponse;
import com.rockey.hospitality.dto.DepartmentDtos.UpdateDepartmentRequest;
import com.rockey.hospitality.entity.Department;
import com.rockey.hospitality.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Here, @RestController returns response data, normally JSON; @RequestMapping sets the shared /api/departments
 * URL.
 * DepartmentController handles Department reads, changes and soft deactivation and delegates business rules
 * to DepartmentService.
 * DTOs describe input/output; Spring Security and service checks, not hidden frontend buttons, enforce
 * permissions.
 */
@RestController
// Groups this controller's routes under /api/departments.
@RequestMapping("/api/departments")
public class DepartmentController {

    // HTTP/annotation study key:
    // @GetMapping handles HTTP GET reads; its path is appended to the controller's base URL.
    // @PostMapping handles HTTP POST creation/actions, here creating a Department.
    // @PutMapping handles HTTP PUT updates, here replacing a Department's editable fields.
    // @DeleteMapping handles DELETE requests; service rules may deactivate, cancel, resolve or withdraw rather
    // than erase rows.
    // @RequestBody reads request JSON into the declared DTO.
    // @PathVariable reads an identifier directly from the URL path.
    // @RequestParam reads a query-string value; required=false makes it optional and defaultValue supplies an
    // omitted value.
    // @Valid runs the DTO's Bean Validation checks before controller business delegation.
    // @Operation and @ApiResponses document the operation and its outcomes; they do not authorize or validate
    // requests.
    // @Content/@Schema describe documented bodies/types, not runtime validation or security.

    /**
     * Injected DepartmentService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final DepartmentService departmentService;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /**
     * List departments.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "List departments", description = "Access: STAFF, ADMIN. Canonical operation: GET /api/departments.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public List<DepartmentResponse> listDepartments(
            @RequestParam(required = false) Boolean active
    ) {
        return departmentService.listDepartments(active).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Create department.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity sets 201 and returns the created DTO.
     */
    @Operation(summary = "Create department", description = "Access: ADMIN. Canonical operation: POST /api/departments.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<DepartmentResponse> createDepartment(
            @Valid
            @RequestBody CreateDepartmentRequest request
    ) {
        Department created = departmentService.createDepartment(
                request.getName(),
                request.getDescription()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    /**
     * Department detail.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Department detail", description = "Access: STAFF, ADMIN. Canonical operation: GET /api/departments/{departmentId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{departmentId}")
    public DepartmentResponse getDepartment(
            @PathVariable Long departmentId) {
        return toResponse(departmentService.getDepartment(departmentId));
    }

    /**
     * Update department.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Update department", description = "Access: ADMIN. Canonical operation: PUT /api/departments/{departmentId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{departmentId}")
    public DepartmentResponse updateDepartment(
            @PathVariable Long departmentId,
            @Valid
            @RequestBody UpdateDepartmentRequest request
    ) {
        Department updated = departmentService.updateDepartment(
                departmentId,
                request.getName(),
                request.getDescription()
        );
        return toResponse(updated);
    }

    /**
     * Deactivate eligible department.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity returns 204 with no body after the service finishes.
     */
    @Operation(summary = "Deactivate eligible department", description = "Access: ADMIN. Canonical operation: DELETE /api/departments/{departmentId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content", content = @Content),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{departmentId}")
    public ResponseEntity<Void> deactivateDepartment(
            @PathVariable Long departmentId) {
        departmentService.deactivateDepartment(departmentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Converts an internal Department entity to the public response fields instead of serializing a JPA entity.
     */
    private DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getName(),
                department.getDescription(),
                department.getActive(),
                department.getCreatedAt(),
                department.getUpdatedAt()
        );
    }
}

package com.rockey.hospitality.controller;

import com.rockey.hospitality.dto.task.TaskSearchCriteria;
import com.rockey.hospitality.dto.common.PageCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.rockey.hospitality.exception.ApiError;

import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.dto.task.AssignTaskRequest;
import com.rockey.hospitality.dto.task.CreateTaskRequest;
import com.rockey.hospitality.dto.task.TaskResponse;
import com.rockey.hospitality.dto.task.UpdateTaskRequest;
import com.rockey.hospitality.entity.TaskPriority;
import com.rockey.hospitality.entity.TaskStatus;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Binds Task HTTP requests and delegates business operations to its service layer.
 * Response DTOs and HTTP statuses are kept separate from JPA entities.
 */
// Registers a web controller whose mapped return values are written as response bodies, normally JSON.
@RestController
// Groups this controller's routes under /api/tasks.
@RequestMapping("/api/tasks")
public class TaskController {

    /**
     * Injected TaskService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final TaskService taskService;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Search operational tasks.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Search operational tasks", description = "Access: ADMIN. Canonical operation: GET /api/tasks.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to the controller's base route.
    @GetMapping
    public PagedResponse<TaskResponse> listTasks(
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) Long departmentId,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) TaskStatus status,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) TaskPriority priority,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) Long assignedEmployeeId,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) Long roomId,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) Long eventId,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) Boolean overdue,
            // Binds this query parameter, defaulting to 0 when omitted.
            @RequestParam(defaultValue = "0") int page,
            // Binds this query parameter, defaulting to 20 when omitted.
            @RequestParam(defaultValue = "20") int size,
            // Binds this query parameter, defaulting to createdAt,desc when omitted.
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return taskService.listTasks(new TaskSearchCriteria(departmentId, status, priority, assignedEmployeeId, roomId, eventId, overdue), new PageCriteria(page, size, sort));
    }

    /**
     * Create task.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity sets 201 and returns the created DTO.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Create task", description = "Access: ADMIN. Canonical operation: POST /api/tasks.")
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
    public ResponseEntity<TaskResponse> createTask(
            // Validates the bound request DTO before the controller delegates to its service.
            @Valid
            // Deserializes the request JSON into this writable DTO.
            @RequestBody CreateTaskRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.createTask(request));
    }

    /**
     * Task detail.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Task detail", description = "Access: Assigned STAFF, ADMIN. Canonical operation: GET /api/tasks/{taskId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to this /{taskId} suffix.
    @GetMapping("/{taskId}")
    public TaskResponse getTask(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long taskId,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        return taskService.getTask(taskId, principal.getId(), principal.getRole());
    }

    /**
     * Update task.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Update task", description = "Access: ADMIN. Canonical operation: PUT /api/tasks/{taskId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP PUT to this /{taskId} suffix.
    @PutMapping("/{taskId}")
    public TaskResponse updateTask(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long taskId,
            // Validates the bound request DTO before the controller delegates to its service.
            @Valid
            // Deserializes the request JSON into this writable DTO.
            @RequestBody UpdateTaskRequest request
    ) {
        return taskService.updateTask(taskId, request);
    }

    /**
     * Cancel eligible task.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Cancel eligible task", description = "Access: ADMIN. Canonical operation: DELETE /api/tasks/{taskId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP DELETE to this /{taskId} suffix.
    @DeleteMapping("/{taskId}")
    public TaskResponse cancelTask(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long taskId) {
        return taskService.cancelTask(taskId);
    }

    /**
     * Complete assigned task.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Complete assigned task", description = "Access: Assigned STAFF, ADMIN. Canonical operation: PATCH /api/tasks/{taskId}/complete.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP PATCH to this /{taskId}/complete suffix.
    @PatchMapping("/{taskId}/complete")
    public TaskResponse completeTask(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long taskId,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        return taskService.completeTask(taskId, principal.getId(), principal.getRole());
    }

    /**
     * Assign/unassign employee.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Assign/unassign employee", description = "Access: ADMIN. Canonical operation: PATCH /api/tasks/{taskId}/assigned-employee.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP PATCH to this /{taskId}/assigned-employee suffix.
    @PatchMapping("/{taskId}/assigned-employee")
    public TaskResponse assignTask(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long taskId,
            // Validates the bound request DTO before the controller delegates to its service.
            @Valid
            // Deserializes the request JSON into this writable DTO.
            @RequestBody AssignTaskRequest request
    ) {
        return taskService.assignTask(taskId, request.getEmployeeId());
    }

    /**
     * Assigned employee tasks.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Assigned employee tasks", description = "Access: Self STAFF, ADMIN. Canonical operation: GET /api/tasks/assigned/{employeeId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to this /assigned/{employeeId} suffix.
    @GetMapping("/assigned/{employeeId}")
    public PagedResponse<TaskResponse> listAssignedTasks(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long employeeId,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) TaskStatus status,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) TaskPriority priority,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) Boolean overdue,
            // Binds this query parameter, defaulting to 0 when omitted.
            @RequestParam(defaultValue = "0") int page,
            // Binds this query parameter, defaulting to 20 when omitted.
            @RequestParam(defaultValue = "20") int size,
            // Binds this query parameter, defaulting to createdAt,desc when omitted.
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        // The service compares this path ID with the authenticated STAFF's actual Employee.
        return taskService.listAssignedTasks(employeeId, status, priority, overdue, new PageCriteria(page, size, sort), principal.getId(), principal.getRole());
    }
}

package com.rockey.hospitality.controller;

import com.rockey.hospitality.dto.CommonDtos.ApiError;
import com.rockey.hospitality.dto.CommonDtos.PageCriteria;
import com.rockey.hospitality.dto.CommonDtos.PagedResponse;
import com.rockey.hospitality.dto.TaskDtos.AssignTaskRequest;
import com.rockey.hospitality.dto.TaskDtos.CreateTaskRequest;
import com.rockey.hospitality.dto.TaskDtos.TaskResponse;
import com.rockey.hospitality.dto.TaskDtos.TaskSearchCriteria;
import com.rockey.hospitality.dto.TaskDtos.UpdateTaskRequest;
import com.rockey.hospitality.entity.Task;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.TaskService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * STUDY NOTE: A Controller is the API entry point for HTTP requests from React or another client.
 * Here, @RestController returns response data, normally JSON; @RequestMapping sets the shared /api/tasks URL.
 * TaskController handles Task lifecycle, assignment and completion and delegates business rules to
 * TaskService.
 * DTOs describe input/output; Spring Security and service checks, not hidden frontend buttons, enforce
 * permissions.
 */
@RestController
// Groups this controller's routes under /api/tasks.
@RequestMapping("/api/tasks")
public class TaskController {

    // HTTP/annotation study key:
    // @GetMapping handles HTTP GET reads; its path is appended to the controller's base URL.
    // @PostMapping handles HTTP POST creation/actions, here creating a Task.
    // @PutMapping handles HTTP PUT updates, here replacing a Task's details, assignee and status.
    // @PatchMapping handles a targeted change, such as Task completion or Room status.
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
    @Operation(summary = "Search operational tasks", description = "Access: ADMIN. Canonical operation: GET /api/tasks.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public PagedResponse<TaskResponse> listTasks(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Task.Status status,
            @RequestParam(required = false) Task.Priority priority,
            @RequestParam(required = false) Long assignedEmployeeId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return taskService.listTasks(new TaskSearchCriteria(departmentId, status, priority, assignedEmployeeId, roomId, eventId, overdue), new PageCriteria(page, size, sort));
    }

    /**
     * Create task.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity sets 201 and returns the created DTO.
     */
    @Operation(summary = "Create task", description = "Access: ADMIN. Canonical operation: POST /api/tasks.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid
            @RequestBody CreateTaskRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.createTask(request));
    }

    /**
     * Task detail.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Task detail", description = "Access: Assigned STAFF, ADMIN. Canonical operation: GET /api/tasks/{taskId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{taskId}")
    public TaskResponse getTask(
            @PathVariable Long taskId,
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        return taskService.getTask(taskId, principal.getId(), principal.getRole());
    }

    /**
     * Update task.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Update task", description = "Access: ADMIN. Canonical operation: PUT /api/tasks/{taskId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{taskId}")
    public TaskResponse updateTask(
            @PathVariable Long taskId,
            @Valid
            @RequestBody UpdateTaskRequest request
    ) {
        return taskService.updateTask(taskId, request);
    }

    /**
     * Cancel eligible task.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Cancel eligible task", description = "Access: ADMIN. Canonical operation: DELETE /api/tasks/{taskId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{taskId}")
    public TaskResponse cancelTask(
            @PathVariable Long taskId) {
        return taskService.cancelTask(taskId);
    }

    /**
     * Complete assigned task.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Complete assigned task", description = "Access: Assigned STAFF, ADMIN. Canonical operation: PATCH /api/tasks/{taskId}/complete.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{taskId}/complete")
    public TaskResponse completeTask(
            @PathVariable Long taskId,
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        return taskService.completeTask(taskId, principal.getId(), principal.getRole());
    }

    /**
     * Assign/unassign employee.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Assign/unassign employee", description = "Access: ADMIN. Canonical operation: PATCH /api/tasks/{taskId}/assigned-employee.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{taskId}/assigned-employee")
    public TaskResponse assignTask(
            @PathVariable Long taskId,
            @Valid
            @RequestBody AssignTaskRequest request
    ) {
        return taskService.assignTask(taskId, request.getEmployeeId());
    }

    /**
     * Assigned employee tasks.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Assigned employee tasks", description = "Access: Self STAFF, ADMIN. Canonical operation: GET /api/tasks/assigned/{employeeId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/assigned/{employeeId}")
    public PagedResponse<TaskResponse> listAssignedTasks(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Task.Status status,
            @RequestParam(required = false) Task.Priority priority,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        // The service compares this path ID with the authenticated STAFF's actual Employee.
        return taskService.listAssignedTasks(employeeId, status, priority, overdue, new PageCriteria(page, size, sort), principal.getId(), principal.getRole());
    }
}

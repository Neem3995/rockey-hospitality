package com.rockey.hospitality.controller;

import com.rockey.hospitality.dto.TaskDtos.*;
import com.rockey.hospitality.service.TaskService;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * STUDY NOTE: A Controller is the HTTP entry point. @RestController returns JSON and @RequestMapping sets /api/tasks.
 * @GetMapping/@PostMapping/@PutMapping/@DeleteMapping map HTTP methods; @PathVariable reads the task id from the URL.
 * @Valid checks the request DTO and @AuthenticationPrincipal supplies the logged-in caller, not a client-sent id.
 * A USER sees and starts/completes only their own assigned work; supervisors create, edit, reassign and cancel it.
 * DELETE cancels the task and keeps its history. TaskService enforces ownership and transitions.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService service;
    public TaskController(TaskService service) { this.service = service; }
    @GetMapping
    @Operation(description = "USER: own assigned tasks only. MANAGER/ADMIN: all tasks.")
    public List<TaskResponse> list(@AuthenticationPrincipal RockeyUserPrincipal actor) { return service.list(actor.getId()); }
    @GetMapping("/{id}")
    @Operation(description = "USER: own assigned task only. MANAGER/ADMIN: any task.")
    public TaskResponse get(@AuthenticationPrincipal RockeyUserPrincipal actor, @PathVariable Long id) { return service.get(actor.getId(), id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @Operation(description = "MANAGER/ADMIN: create and assign tasks to active USER housekeepers.")
    public TaskResponse create(@AuthenticationPrincipal RockeyUserPrincipal actor, @Valid @RequestBody TaskRequest request) {
        return service.create(actor.getId(), request);
    }
    @PutMapping("/{id}")
    @Operation(description = "MANAGER/ADMIN: edit or reassign non-terminal tasks.")
    public TaskResponse update(@AuthenticationPrincipal RockeyUserPrincipal actor, @PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return service.update(actor.getId(), id, request);
    }
    @PutMapping("/{id}/status")
    @Operation(description = "USER: start own ASSIGNED task or complete own IN_PROGRESS task. MANAGER/ADMIN: cancel only.")
    public TaskResponse status(@AuthenticationPrincipal RockeyUserPrincipal actor, @PathVariable Long id, @Valid @RequestBody TaskStatusRequest request) {
        return service.status(actor.getId(), id, request.getStatus());
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(description = "MANAGER/ADMIN: cancel non-terminal tasks while retaining history.")
    public void cancel(@AuthenticationPrincipal RockeyUserPrincipal actor, @PathVariable Long id) { service.cancel(actor.getId(), id); }
}

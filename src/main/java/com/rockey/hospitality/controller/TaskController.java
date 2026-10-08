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
 * STUDY NOTE: A controller connects an HTTP request to a service; it does not own the work rules.
 * First Spring matches /api/tasks and reads the URL, JSON DTO and authenticated principal.
 * Next we pass actor.getId() and the requested task id to TaskService: who is asking, and which work.
 * TaskService returns a safe TaskResponse (or list); Spring writes that as JSON for the client.
 * USER works only on their own tasks. MANAGER/ADMIN manage work, but do not execute it.
 * SecurityConfiguration checks route access; TaskService checks ownership and transitions.
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
        // First get who is calling from Spring, which task from the URL, and the next status from JSON.
        // These are arguments to service.status; its actorId/id/next parameters receive those values.
        // The service checks ownership and changes Task + Room; we return its DTO, not either entity.
        return service.status(actor.getId(), id, request.getStatus());
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(description = "MANAGER/ADMIN: cancel non-terminal tasks while retaining history.")
    public void cancel(@AuthenticationPrincipal RockeyUserPrincipal actor, @PathVariable Long id) { service.cancel(actor.getId(), id); }
}

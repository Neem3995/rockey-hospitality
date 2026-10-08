package com.rockey.hospitality.controller;

import com.rockey.hospitality.dto.RoomDtos.*;
import com.rockey.hospitality.service.RoomService;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * STUDY NOTE: Spring routes /api/rooms requests here after the security checks.
 * We receive a room id, validated DTO and/or the authenticated principal, then call RoomService.
 * The service returns a room or inspection DTO; this controller supplies the HTTP response.
 * For inspection, actor.getId() identifies the supervisor. The client does not choose the inspector.
 * DELETE means deactivate, not erase. Permission and readiness rules stay in SecurityConfiguration
 * and RoomService, not in the frontend controls or this routing layer.
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService service;
    public RoomController(RoomService service) { this.service = service; }
    @GetMapping
    @Operation(description = "MANAGER/ADMIN: list rooms.")
    public List<RoomResponse> list(@AuthenticationPrincipal RockeyUserPrincipal actor) { return service.list(actor.getId()); }
    @GetMapping("/{id}")
    @Operation(description = "MANAGER/ADMIN: view a room.")
    public RoomResponse get(@AuthenticationPrincipal RockeyUserPrincipal actor, @PathVariable Long id) { return service.get(actor.getId(), id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @Operation(description = "MANAGER/ADMIN: create rooms.")
    public RoomResponse create(@AuthenticationPrincipal RockeyUserPrincipal actor, @Valid @RequestBody RoomRequest request) {
        return service.create(actor.getId(), request);
    }
    @PutMapping("/{id}")
    @Operation(description = "MANAGER/ADMIN: edit rooms.")
    public RoomResponse update(@AuthenticationPrincipal RockeyUserPrincipal actor, @PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return service.update(actor.getId(), id, request);
    }
    @PutMapping("/{id}/status")
    @Operation(description = "MANAGER/ADMIN: manage permitted room status transitions.")
    public RoomResponse status(@AuthenticationPrincipal RockeyUserPrincipal actor, @PathVariable Long id, @Valid @RequestBody RoomStatusRequest request) {
        return service.status(actor.getId(), id, request.getStatus());
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(description = "MANAGER/ADMIN: soft-deactivate rooms without active work or pending inspection.")
    public void deactivate(@AuthenticationPrincipal RockeyUserPrincipal actor, @PathVariable Long id) { service.deactivate(actor.getId(), id); }
    @GetMapping("/{id}/inspections")
    @Operation(description = "MANAGER/ADMIN: read retained room inspection history.")
    public List<InspectionResponse> inspections(@AuthenticationPrincipal RockeyUserPrincipal actor, @PathVariable Long id) {
        return service.inspectionHistory(actor.getId(), id);
    }
    @PostMapping("/{id}/inspections") @ResponseStatus(HttpStatus.CREATED)
    @Operation(description = "MANAGER/ADMIN: inspect completed cleaning work; PASS makes READY, FAIL makes DIRTY.")
    public InspectionResponse inspect(@AuthenticationPrincipal RockeyUserPrincipal actor, @PathVariable Long id, @Valid @RequestBody InspectionRequest request) {
        return service.inspect(actor.getId(), id, request);
    }
}

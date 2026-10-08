package com.rockey.hospitality.controller;

import com.rockey.hospitality.dto.UserDtos.*;
import com.rockey.hospitality.service.UserService;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * STUDY NOTE: This is the Team API entry point for matching /api/users requests.
 * First Spring supplies the principal, URL id and validated account DTO. Next we call UserService.
 * We send back safe user DTOs; DELETE returns no body after deactivation, not physical deletion.
 * MANAGER can view/create USER accounts; ADMIN can also manage non-ADMIN accounts.
 * UserService checks targets and grants. This controller does not bootstrap an ADMIN or hash passwords.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service = service; }
    @GetMapping
    @Operation(description = "MANAGER: USER accounts only. ADMIN: all accounts.")
    public List<UserResponse> list(@AuthenticationPrincipal RockeyUserPrincipal actor) { return service.list(actor.getId()); }
    @GetMapping("/{id}")
    @Operation(description = "MANAGER: USER accounts only. ADMIN: any account.")
    public UserResponse get(@AuthenticationPrincipal RockeyUserPrincipal actor, @PathVariable Long id) { return service.get(actor.getId(), id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @Operation(description = "MANAGER: create USER accounts. ADMIN: create USER or MANAGER accounts.")
    public UserResponse create(@AuthenticationPrincipal RockeyUserPrincipal actor, @Valid @RequestBody CreateUserRequest request) {
        return service.create(actor.getId(), request);
    }
    @PutMapping("/{id}")
    @Operation(description = "ADMIN: update non-ADMIN accounts only.")
    public UserResponse update(@AuthenticationPrincipal RockeyUserPrincipal actor, @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(actor.getId(), id, request);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(description = "ADMIN: soft-deactivate non-ADMIN accounts only.")
    public void deactivate(@AuthenticationPrincipal RockeyUserPrincipal actor, @PathVariable Long id) { service.deactivate(actor.getId(), id); }
}

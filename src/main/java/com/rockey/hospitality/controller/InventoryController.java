package com.rockey.hospitality.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.rockey.hospitality.exception.ApiError;

import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.dto.inventory.CreateInventoryItemRequest;
import com.rockey.hospitality.dto.inventory.InventoryItemResponse;
import com.rockey.hospitality.dto.inventory.UpdateInventoryItemRequest;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.InventoryService;
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
 * Binds Inventory HTTP requests and delegates business operations to its service layer.
 * Response DTOs and HTTP statuses are kept separate from JPA entities.
 */
// Registers a web controller whose mapped return values are written as response bodies, normally JSON.
@RestController
// Groups this controller's routes under /api/inventory.
@RequestMapping("/api/inventory")
public class InventoryController {

    /**
     * Injected InventoryService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final InventoryService inventoryService;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Department-scoped/all inventory.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Department-scoped/all inventory", description = "Access: STAFF scoped, ADMIN. Canonical operation: GET /api/inventory.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to the controller's base route.
    @GetMapping
    public PagedResponse<InventoryItemResponse> listInventory(
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) Long departmentId,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) Boolean active,
            // Binds this query parameter, defaulting to 0 when omitted.
            @RequestParam(defaultValue = "0") int page,
            // Binds this query parameter, defaulting to 20 when omitted.
            @RequestParam(defaultValue = "20") int size,
            // Binds this query parameter, defaulting to name,asc when omitted.
            @RequestParam(defaultValue = "name,asc") String sort,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        return inventoryService.listInventory(departmentId, active, page, size, sort,
                principal.getId(), principal.getRole());
    }

    /**
     * Create inventory item.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity sets 201 and returns the created DTO.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Create inventory item", description = "Access: ADMIN. Canonical operation: POST /api/inventory.")
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
    public ResponseEntity<InventoryItemResponse> createInventoryItem(
            // Validates the bound request DTO before the controller delegates to its service.
            @Valid
            // Deserializes the request JSON into this writable DTO.
            @RequestBody CreateInventoryItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.createInventoryItem(request));
    }

    /**
     * Inventory detail.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Inventory detail", description = "Access: STAFF scoped, ADMIN. Canonical operation: GET /api/inventory/{itemId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to this /{itemId} suffix.
    @GetMapping("/{itemId}")
    public InventoryItemResponse getInventoryItem(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long itemId,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        return inventoryService.getInventoryItem(itemId, principal.getId(), principal.getRole());
    }

    /**
     * Update/restock item.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Update/restock item", description = "Access: ADMIN. Canonical operation: PUT /api/inventory/{itemId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP PUT to this /{itemId} suffix.
    @PutMapping("/{itemId}")
    public InventoryItemResponse updateInventoryItem(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long itemId,
            // Validates the bound request DTO before the controller delegates to its service.
            @Valid
            // Deserializes the request JSON into this writable DTO.
            @RequestBody UpdateInventoryItemRequest request) {
        return inventoryService.updateInventoryItem(itemId, request);
    }

    /**
     * Deactivate inventory item.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity returns 204 with no body after the service finishes.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Deactivate inventory item", description = "Access: ADMIN. Canonical operation: DELETE /api/inventory/{itemId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content", content = @Content),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP DELETE to this /{itemId} suffix.
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> deactivateInventoryItem(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long itemId) {
        inventoryService.deactivateInventoryItem(itemId);
        return ResponseEntity.noContent().build();
    }
}

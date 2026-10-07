package com.rockey.hospitality.controller;

import com.rockey.hospitality.dto.CommonDtos.ApiError;
import com.rockey.hospitality.dto.CommonDtos.PagedResponse;
import com.rockey.hospitality.dto.InventoryDtos.CreateInventoryItemRequest;
import com.rockey.hospitality.dto.InventoryDtos.InventoryItemResponse;
import com.rockey.hospitality.dto.InventoryDtos.UpdateInventoryItemRequest;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.InventoryService;
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
 * Here, @RestController returns response data, normally JSON; @RequestMapping sets the shared /api/inventory URL.
 * InventoryController handles stock reads, updates and soft deactivation and delegates business rules to
 * InventoryService.
 * DTOs describe input/output; Spring Security and service checks, not hidden frontend buttons, enforce
 * permissions.
 */
@RestController
// Groups this controller's routes under /api/inventory.
@RequestMapping("/api/inventory")
public class InventoryController {

    // HTTP/annotation study key:
    // @GetMapping handles HTTP GET reads; its path is appended to the controller's base URL.
    // @PostMapping handles HTTP POST creation/actions, here creating an inventory item.
    // @PutMapping handles HTTP PUT updates, here replacing stock details, including absolute restock quantities.
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
    @Operation(summary = "Department-scoped/all inventory", description = "Access: STAFF scoped, ADMIN. Canonical operation: GET /api/inventory.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public PagedResponse<InventoryItemResponse> listInventory(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort,
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        return inventoryService.listInventory(departmentId, active, page, size, sort,
                principal.getId(), principal.getRole());
    }

    /**
     * Create inventory item.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity sets 201 and returns the created DTO.
     */
    @Operation(summary = "Create inventory item", description = "Access: ADMIN. Canonical operation: POST /api/inventory.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<InventoryItemResponse> createInventoryItem(
            @Valid
            @RequestBody CreateInventoryItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.createInventoryItem(request));
    }

    /**
     * Inventory detail.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Inventory detail", description = "Access: STAFF scoped, ADMIN. Canonical operation: GET /api/inventory/{itemId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{itemId}")
    public InventoryItemResponse getInventoryItem(
            @PathVariable Long itemId,
            @AuthenticationPrincipal RockeyUserPrincipal principal) {
        return inventoryService.getInventoryItem(itemId, principal.getId(), principal.getRole());
    }

    /**
     * Update/restock item.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Update/restock item", description = "Access: ADMIN. Canonical operation: PUT /api/inventory/{itemId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{itemId}")
    public InventoryItemResponse updateInventoryItem(
            @PathVariable Long itemId,
            @Valid
            @RequestBody UpdateInventoryItemRequest request) {
        return inventoryService.updateInventoryItem(itemId, request);
    }

    /**
     * Deactivate inventory item.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity returns 204 with no body after the service finishes.
     */
    @Operation(summary = "Deactivate inventory item", description = "Access: ADMIN. Canonical operation: DELETE /api/inventory/{itemId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content", content = @Content),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> deactivateInventoryItem(
            @PathVariable Long itemId) {
        inventoryService.deactivateInventoryItem(itemId);
        return ResponseEntity.noContent().build();
    }
}

package com.rockey.hospitality.controller;

import com.rockey.hospitality.dto.common.PageCriteria;
import com.rockey.hospitality.dto.room.RoomSearchCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.rockey.hospitality.exception.ApiError;

import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.dto.room.CreateRoomRequest;
import com.rockey.hospitality.dto.room.RoomResponse;
import com.rockey.hospitality.dto.room.UpdateRoomRequest;
import com.rockey.hospitality.dto.room.UpdateRoomStatusRequest;
import com.rockey.hospitality.entity.RoomStatus;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.RoomService;
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
 * Binds Room HTTP requests and delegates business operations to its service layer.
 * Response DTOs and HTTP statuses are kept separate from JPA entities.
 */
// Registers a web controller whose mapped return values are written as response bodies, normally JSON.
@RestController
// Groups this controller's routes under /api/rooms.
@RequestMapping("/api/rooms")
public class RoomController {

    /**
     * Injected RoomService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final RoomService roomService;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    /**
     * Paginated/filter rooms.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Paginated/filter rooms", description = "Access: STAFF, ADMIN. Canonical operation: GET /api/rooms.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to the controller's base route.
    @GetMapping
    public PagedResponse<RoomResponse> listRooms(
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) RoomStatus status,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) Integer floor,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(name = "type", required = false) String roomType,
            // Binds an optional query parameter; omitted filters arrive as null.
            @RequestParam(required = false) Boolean active,
            // Binds this query parameter, defaulting to 0 when omitted.
            @RequestParam(defaultValue = "0") int page,
            // Binds this query parameter, defaulting to 20 when omitted.
            @RequestParam(defaultValue = "20") int size,
            // Binds this query parameter, defaulting to roomNumber,asc when omitted.
            @RequestParam(defaultValue = "roomNumber,asc") String sort,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        return roomService.listRooms(new RoomSearchCriteria(status, floor, roomType, active), new PageCriteria(page, size, sort), principal.getRole());
    }

    /**
     * Create room.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity sets 201 and returns the created DTO.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Create room", description = "Access: ADMIN. Canonical operation: POST /api/rooms.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP POST to the controller's base route.
    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            // Validates the bound request DTO before the controller delegates to its service.
            @Valid
            // Deserializes the request JSON into this writable DTO.
            @RequestBody CreateRoomRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roomService.createRoom(request));
    }

    /**
     * Room detail.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Room detail", description = "Access: STAFF, ADMIN. Canonical operation: GET /api/rooms/{roomId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to this /{roomId} suffix.
    @GetMapping("/{roomId}")
    public RoomResponse getRoom(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long roomId,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        return roomService.getRoom(roomId, principal.getRole());
    }

    /**
     * Update room details/arrival time.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Update room details/arrival time", description = "Access: ADMIN. Canonical operation: PUT /api/rooms/{roomId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP PUT to this /{roomId} suffix.
    @PutMapping("/{roomId}")
    public RoomResponse updateRoom(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long roomId,
            // Validates the bound request DTO before the controller delegates to its service.
            @Valid
            // Deserializes the request JSON into this writable DTO.
            @RequestBody UpdateRoomRequest request
    ) {
        return roomService.updateRoom(roomId, request);
    }

    /**
     * Deactivate eligible room.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity returns 204 with no body after the service finishes.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Deactivate eligible room", description = "Access: ADMIN. Canonical operation: DELETE /api/rooms/{roomId}.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content", content = @Content),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP DELETE to this /{roomId} suffix.
    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deactivateRoom(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long roomId) {
        roomService.deactivateRoom(roomId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Apply room transition.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Apply room transition", description = "Access: STAFF, ADMIN. Canonical operation: PATCH /api/rooms/{roomId}/status.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP PATCH to this /{roomId}/status suffix.
    @PatchMapping("/{roomId}/status")
    public RoomResponse updateStatus(
            // Binds this value from the matching identifier in the route path.
            @PathVariable Long roomId,
            // Validates the bound request DTO before the controller delegates to its service.
            @Valid
            // Deserializes the request JSON into this writable DTO.
            @RequestBody UpdateRoomStatusRequest request,
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        return roomService.updateStatus(
                roomId,
                request.getStatus(),
                principal.getId(),
                principal.getRole()
        );
    }
}

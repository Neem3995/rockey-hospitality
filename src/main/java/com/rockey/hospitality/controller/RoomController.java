package com.rockey.hospitality.controller;

import com.rockey.hospitality.dto.CommonDtos.ApiError;
import com.rockey.hospitality.dto.CommonDtos.PageCriteria;
import com.rockey.hospitality.dto.CommonDtos.PagedResponse;
import com.rockey.hospitality.dto.RoomDtos.CreateRoomRequest;
import com.rockey.hospitality.dto.RoomDtos.RoomResponse;
import com.rockey.hospitality.dto.RoomDtos.RoomSearchCriteria;
import com.rockey.hospitality.dto.RoomDtos.UpdateRoomRequest;
import com.rockey.hospitality.dto.RoomDtos.UpdateRoomStatusRequest;
import com.rockey.hospitality.entity.Room;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.RoomService;
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
 * Here, @RestController returns response data, normally JSON; @RequestMapping sets the shared /api/rooms URL.
 * RoomController handles Room details and turnover status changes and delegates business rules to
 * RoomService.
 * DTOs describe input/output; Spring Security and service checks, not hidden frontend buttons, enforce
 * permissions.
 */
@RestController
// Groups this controller's routes under /api/rooms.
@RequestMapping("/api/rooms")
public class RoomController {

    // HTTP/annotation study key:
    // @GetMapping handles HTTP GET reads; its path is appended to the controller's base URL.
    // @PostMapping handles HTTP POST creation/actions, here creating a Room.
    // @PutMapping handles HTTP PUT updates, here replacing Room details and arrival time.
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
    @Operation(summary = "Paginated/filter rooms", description = "Access: STAFF, ADMIN. Canonical operation: GET /api/rooms.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public PagedResponse<RoomResponse> listRooms(
            @RequestParam(required = false) Room.Status status,
            @RequestParam(required = false) Integer floor,
            @RequestParam(name = "type", required = false) String roomType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "roomNumber,asc") String sort,
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        return roomService.listRooms(new RoomSearchCriteria(status, floor, roomType, active), new PageCriteria(page, size, sort), principal.getRole());
    }

    /**
     * Create room.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity sets 201 and returns the created DTO.
     */
    @Operation(summary = "Create room", description = "Access: ADMIN. Canonical operation: POST /api/rooms.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @Valid
            @RequestBody CreateRoomRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roomService.createRoom(request));
    }

    /**
     * Room detail.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Room detail", description = "Access: STAFF, ADMIN. Canonical operation: GET /api/rooms/{roomId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{roomId}")
    public RoomResponse getRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        return roomService.getRoom(roomId, principal.getRole());
    }

    /**
     * Update room details/arrival time.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Update room details/arrival time", description = "Access: ADMIN. Canonical operation: PUT /api/rooms/{roomId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{roomId}")
    public RoomResponse updateRoom(
            @PathVariable Long roomId,
            @Valid
            @RequestBody UpdateRoomRequest request
    ) {
        return roomService.updateRoom(roomId, request);
    }

    /**
     * Deactivate eligible room.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     * ResponseEntity returns 204 with no body after the service finishes.
     */
    @Operation(summary = "Deactivate eligible room", description = "Access: ADMIN. Canonical operation: DELETE /api/rooms/{roomId}.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content", content = @Content),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deactivateRoom(
            @PathVariable Long roomId) {
        roomService.deactivateRoom(roomId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Apply room transition.
     * Binds HTTP inputs and delegates the operation to the service, which enforces domain rules and record scope.
     */
    @Operation(summary = "Apply room transition", description = "Access: STAFF, ADMIN. Canonical operation: PATCH /api/rooms/{roomId}/status.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Role, ownership or eligibility denied", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{roomId}/status")
    public RoomResponse updateStatus(
            @PathVariable Long roomId,
            @Valid
            @RequestBody UpdateRoomStatusRequest request,
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

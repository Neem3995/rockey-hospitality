package com.rockey.hospitality.dto.room;

import com.rockey.hospitality.entity.RoomStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Writable update room status JSON DTO, separate from the entity and returned fields.
 * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
 */
public class UpdateRoomStatusRequest {

    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull(message = "Room status is required.")
    private RoomStatus status;

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }
}

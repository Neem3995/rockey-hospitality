package com.rockey.hospitality.dto.room;

import com.rockey.hospitality.entity.RoomStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateRoomStatusRequest {

    @NotNull(message = "Room status is required.")
    private RoomStatus status;

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }
}

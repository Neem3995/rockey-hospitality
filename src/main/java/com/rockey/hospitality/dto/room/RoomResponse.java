package com.rockey.hospitality.dto.room;

import com.rockey.hospitality.entity.RoomStatus;

import java.time.LocalDateTime;

public class RoomResponse {

    private final Long id;
    private final String roomNumber;
    private final String roomType;
    private final RoomStatus status;
    private final Integer floor;
    private final LocalDateTime nextArrivalAt;
    private final Boolean active;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public RoomResponse(
            Long id,
            String roomNumber,
            String roomType,
            RoomStatus status,
            Integer floor,
            LocalDateTime nextArrivalAt,
            Boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.status = status;
        this.floor = floor;
        this.nextArrivalAt = nextArrivalAt;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public String getRoomType() {
        return roomType;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public Integer getFloor() {
        return floor;
    }

    public LocalDateTime getNextArrivalAt() {
        return nextArrivalAt;
    }

    public Boolean getActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

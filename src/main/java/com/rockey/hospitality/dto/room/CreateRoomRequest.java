package com.rockey.hospitality.dto.room;

import com.rockey.hospitality.entity.RoomStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class CreateRoomRequest {

    @NotBlank(message = "Room number is required.")
    private String roomNumber;

    @NotBlank(message = "Room type is required.")
    @Size(min = 2, max = 50, message = "Room type must be between 2 and 50 characters.")
    private String roomType;

    @NotNull(message = "Floor is required.")
    @Min(value = 1, message = "Floor must be between 1 and 99.")
    @Max(value = 99, message = "Floor must be between 1 and 99.")
    private Integer floor;

    private RoomStatus initialStatus;

    @FutureOrPresent(message = "Next arrival time must be current or future.")
    private LocalDateTime nextArrivalAt;

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }

    public Integer getFloor() {
        return floor;
    }

    public void setFloor(Integer floor) {
        this.floor = floor;
    }

    public RoomStatus getInitialStatus() {
        return initialStatus;
    }

    public void setInitialStatus(RoomStatus initialStatus) {
        this.initialStatus = initialStatus;
    }

    public LocalDateTime getNextArrivalAt() {
        return nextArrivalAt;
    }

    public void setNextArrivalAt(LocalDateTime nextArrivalAt) {
        this.nextArrivalAt = nextArrivalAt;
    }
}

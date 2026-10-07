package com.rockey.hospitality.dto.room;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Writable update room JSON DTO, separate from the entity and returned fields.
 * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
 */
public class UpdateRoomRequest {

    /**
     * Operational Room classification used in display and type filtering.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank(message = "Room type is required.")
    // Checks supplied text length from 2 to 50 characters; required text is checked separately.
    @Size(min = 2, max = 50, message = "Room type must be between 2 and 50 characters.")
    private String roomType;

    /**
     * Room floor or optional floor filter; service validation constrains supplied values.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull(message = "Floor is required.")
    // Checks that a supplied number is at least 1.
    @Min(value = 1, message = "Floor must be between 1 and 99.")
    // Checks that a supplied number is at most 99.
    @Max(value = 99, message = "Floor must be between 1 and 99.")
    private Integer floor;

    /**
     * Optional server-local arrival readiness time, not a Reservation or Booking relationship.
     */
    // Allows a supplied timestamp at the current time or in the future.
    @FutureOrPresent(message = "Next arrival time must be current or future.")
    private LocalDateTime nextArrivalAt;

    /**
     * Soft-lifecycle flag; inactive rows retain their identity and history.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull(message = "Active is required.")
    private Boolean active;

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

    public LocalDateTime getNextArrivalAt() {
        return nextArrivalAt;
    }

    public void setNextArrivalAt(LocalDateTime nextArrivalAt) {
        this.nextArrivalAt = nextArrivalAt;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}

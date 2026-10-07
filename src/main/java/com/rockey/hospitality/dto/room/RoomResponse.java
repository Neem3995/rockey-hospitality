package com.rockey.hospitality.dto.room;

import com.rockey.hospitality.entity.RoomStatus;

import java.time.LocalDateTime;

/**
 * Safe room response DTO built from validated service results instead of serializing the entity.
 */
public class RoomResponse {

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    private final Long id;
    /**
     * Unique operational Room identifier; it is not a database ID or a booking.
     */
    private final String roomNumber;
    /**
     * Operational Room classification used in display and type filtering.
     */
    private final String roomType;
    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    private final RoomStatus status;
    /**
     * Room floor or optional floor filter; service validation constrains supplied values.
     */
    private final Integer floor;
    /**
     * Optional server-local arrival readiness time, not a Reservation or Booking relationship.
     */
    private final LocalDateTime nextArrivalAt;
    /**
     * Soft-lifecycle flag; inactive rows retain their identity and history.
     */
    private final Boolean active;
    /**
     * Server-local creation timestamp retained for history.
     */
    private final LocalDateTime createdAt;
    /**
     * Server-local timestamp of the latest persisted entity update.
     */
    private final LocalDateTime updatedAt;

    /**
     * Packages the listed response fields supplied by the service without serializing a persistence entity.
     */
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

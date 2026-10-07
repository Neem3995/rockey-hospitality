package com.rockey.hospitality.dto.task;

/**
 * Shallow task room response DTO exposing only the listed fields, not a complete JPA relationship graph.
 */
public class TaskRoomSummary {

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    private final Long id;
    /**
     * Unique operational Room identifier; it is not a database ID or a booking.
     */
    private final String roomNumber;

    /**
     * Packages the listed response fields supplied by the service without serializing a persistence entity.
     */
    public TaskRoomSummary(Long id, String roomNumber) {
        this.id = id;
        this.roomNumber = roomNumber;
    }

    public Long getId() {
        return id;
    }

    public String getRoomNumber() {
        return roomNumber;
    }
}

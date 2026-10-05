package com.rockey.hospitality.dto.task;

public class TaskRoomSummary {

    private final Long id;
    private final String roomNumber;

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

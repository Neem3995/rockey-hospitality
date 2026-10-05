package com.rockey.hospitality.dto.event;

import com.rockey.hospitality.entity.EventStatus;

import java.time.LocalDateTime;

public class EventResponse {

    private final Long id;
    private final String title;
    private final String description;
    private final LocalDateTime eventDateTime;
    private final String location;
    private final Integer capacity;
    private final EventStatus status;
    private final long registeredCount;
    private final long remainingCapacity;
    private final long taskCount;
    private final long completedTaskCount;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public EventResponse(
            Long id,
            String title,
            String description,
            LocalDateTime eventDateTime,
            String location,
            Integer capacity,
            EventStatus status,
            long registeredCount,
            long remainingCapacity,
            long taskCount,
            long completedTaskCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.eventDateTime = eventDateTime;
        this.location = location;
        this.capacity = capacity;
        this.status = status;
        this.registeredCount = registeredCount;
        this.remainingCapacity = remainingCapacity;
        this.taskCount = taskCount;
        this.completedTaskCount = completedTaskCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDateTime getEventDateTime() { return eventDateTime; }
    public String getLocation() { return location; }
    public Integer getCapacity() { return capacity; }
    public EventStatus getStatus() { return status; }
    public long getRegisteredCount() { return registeredCount; }
    public long getRemainingCapacity() { return remainingCapacity; }
    public long getTaskCount() { return taskCount; }
    public long getCompletedTaskCount() { return completedTaskCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

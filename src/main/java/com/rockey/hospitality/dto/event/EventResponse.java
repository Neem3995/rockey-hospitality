package com.rockey.hospitality.dto.event;

import com.rockey.hospitality.entity.EventStatus;

import java.time.LocalDateTime;

/**
 * Safe event response DTO built from validated service results instead of serializing the entity.
 */
public class EventResponse {

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    private final Long id;
    /**
     * Human-readable work or Event title.
     */
    private final String title;
    /**
     * Optional descriptive text; services normalize blank values where required.
     */
    private final String description;
    /**
     * Server-local Event schedule used by registration and lifecycle eligibility.
     */
    private final LocalDateTime eventDateTime;
    /**
     * Event location shown in details and registration summaries.
     */
    private final String location;
    /**
     * Maximum registration count allowed for the Event.
     */
    private final Integer capacity;
    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    private final EventStatus status;
    /**
     * Count of retained User registrations for this Event.
     */
    private final long registeredCount;
    /**
     * Capacity minus retained registrations, clamped to zero.
     */
    private final long remainingCapacity;
    /**
     * All retained preparation Tasks linked to this Event, including terminal history.
     */
    private final long taskCount;
    /**
     * Tasks with status COMPLETED in the dashboard, or completed preparation Tasks in an Event response.
     */
    private final long completedTaskCount;
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

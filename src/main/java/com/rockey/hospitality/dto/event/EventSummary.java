package com.rockey.hospitality.dto.event;

import com.rockey.hospitality.entity.EventStatus;

import java.time.LocalDateTime;

/**
 * Shallow event response DTO exposing only the listed fields, not a complete JPA relationship graph.
 */
public class EventSummary {

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    private final Long id;
    /**
     * Human-readable work or Event title.
     */
    private final String title;
    /**
     * Server-local Event schedule used by registration and lifecycle eligibility.
     */
    private final LocalDateTime eventDateTime;
    /**
     * Event location shown in details and registration summaries.
     */
    private final String location;
    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    private final EventStatus status;
    /**
     * Capacity minus retained registrations, clamped to zero.
     */
    private final long remainingCapacity;

    /**
     * Packages the listed response fields supplied by the service without serializing a persistence entity.
     */
    public EventSummary(
            Long id,
            String title,
            LocalDateTime eventDateTime,
            String location,
            EventStatus status,
            long remainingCapacity
    ) {
        this.id = id;
        this.title = title;
        this.eventDateTime = eventDateTime;
        this.location = location;
        this.status = status;
        this.remainingCapacity = remainingCapacity;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public LocalDateTime getEventDateTime() { return eventDateTime; }
    public String getLocation() { return location; }
    public EventStatus getStatus() { return status; }
    public long getRemainingCapacity() { return remainingCapacity; }
}

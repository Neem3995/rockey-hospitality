package com.rockey.hospitality.dto.task;

import com.rockey.hospitality.entity.EventStatus;

import java.time.LocalDateTime;

/**
 * Shallow task event response DTO exposing only the listed fields, not a complete JPA relationship graph.
 */
public class TaskEventSummary {

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
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    private final EventStatus status;

    /**
     * Packages the listed response fields supplied by the service without serializing a persistence entity.
     */
    public TaskEventSummary(
            Long id,
            String title,
            LocalDateTime eventDateTime,
            EventStatus status
    ) {
        this.id = id;
        this.title = title;
        this.eventDateTime = eventDateTime;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public LocalDateTime getEventDateTime() { return eventDateTime; }
    public EventStatus getStatus() { return status; }
}

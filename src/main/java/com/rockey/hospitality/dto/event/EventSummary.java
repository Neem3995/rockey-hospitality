package com.rockey.hospitality.dto.event;

import com.rockey.hospitality.entity.EventStatus;

import java.time.LocalDateTime;

public class EventSummary {

    private final Long id;
    private final String title;
    private final LocalDateTime eventDateTime;
    private final String location;
    private final EventStatus status;
    private final long remainingCapacity;

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

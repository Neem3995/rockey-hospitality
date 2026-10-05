package com.rockey.hospitality.dto.task;

import com.rockey.hospitality.entity.EventStatus;

import java.time.LocalDateTime;

public class TaskEventSummary {

    private final Long id;
    private final String title;
    private final LocalDateTime eventDateTime;
    private final EventStatus status;

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

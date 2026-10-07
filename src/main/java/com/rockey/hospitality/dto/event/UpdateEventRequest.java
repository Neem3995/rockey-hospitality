package com.rockey.hospitality.dto.event;

import com.rockey.hospitality.entity.EventStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Writable update event JSON DTO, separate from the entity and returned fields.
 * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
 */
public class UpdateEventRequest {

    /**
     * Human-readable work or Event title.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank(message = "Title is required.")
    // Checks supplied text length from 3 to 120 characters; required text is checked separately.
    @Size(min = 3, max = 120, message = "Title must be between 3 and 120 characters.")
    private String title;

    /**
     * Optional descriptive text; services normalize blank values where required.
     */
    // Checks supplied text length up to 1000 characters; required text is checked separately.
    @Size(max = 1000, message = "Description must not exceed 1000 characters.")
    private String description;

    /**
     * Server-local Event schedule used by registration and lifecycle eligibility.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull(message = "Event date and time are required.")
    private LocalDateTime eventDateTime;

    /**
     * Event location shown in details and registration summaries.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank(message = "Location is required.")
    // Checks supplied text length from 2 to 120 characters; required text is checked separately.
    @Size(min = 2, max = 120, message = "Location must be between 2 and 120 characters.")
    private String location;

    /**
     * Maximum registration count allowed for the Event.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull(message = "Capacity is required.")
    // Checks that a supplied number is at least 1.
    @Min(value = 1, message = "Capacity must be between 1 and 10000.")
    // Checks that a supplied number is at most 10000.
    @Max(value = 10000, message = "Capacity must be between 1 and 10000.")
    private Integer capacity;

    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull(message = "Status is required.")
    private EventStatus status;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getEventDateTime() {
        return eventDateTime;
    }

    public void setEventDateTime(LocalDateTime eventDateTime) {
        this.eventDateTime = eventDateTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }
}

package com.rockey.hospitality.dto;

import com.rockey.hospitality.entity.Event;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * STUDY NOTE: DTO means Data Transfer Object: a plain Java type that carries request, response or internal
 * input data.
 * This container groups Event creation/update inputs, responses and registration/Event summaries.
 * Controllers and services use these types to separate the API contract from JPA entities and avoid
 * exposing database objects directly.
 * Static nested types need no container instance; validation/JSON annotations describe data, not access
 * permissions.
 */
public final class EventDtos {

    // Validation study key (the numbers/patterns are specified on each annotated field):
    // @NotBlank requires non-null text containing at least one non-whitespace character.
    // @NotNull requires a value; it does not check text length or a numeric range.
    // @Size checks length/count against the declared min/max (text length for the fields here).
    // @Min sets an inclusive minimum for a supplied number.
    // @Max sets an inclusive maximum for a supplied number.
    // @Future checks that a supplied date/time is strictly after the validator's current time.
    // Most shape/range validators accept null; @NotNull or @NotBlank supplies required-value checks.

    // Namespace only; callers construct the nested types instead.
    private EventDtos() { }

    /**
     * Writable create event JSON DTO, separate from the entity and returned fields.
     * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
     */
    public static class CreateEventRequest {

        /**
         * Human-readable work or Event title.
         */
        @NotBlank(message = "Title is required.")
        @Size(min = 3, max = 120, message = "Title must be between 3 and 120 characters.")
        private String title;

        /**
         * Optional descriptive text; services normalize blank values where required.
         */
        @Size(max = 1000, message = "Description must not exceed 1000 characters.")
        private String description;

        /**
         * Server-local Event schedule used by registration and lifecycle eligibility.
         */
        @NotNull(message = "Event date and time are required.")
        // Requires a supplied timestamp to be strictly in the future.
        @Future(message = "Event date and time must be in the future.")
        private LocalDateTime eventDateTime;

        /**
         * Event location shown in details and registration summaries.
         */
        @NotBlank(message = "Location is required.")
        @Size(min = 2, max = 120, message = "Location must be between 2 and 120 characters.")
        private String location;

        /**
         * Maximum registration count allowed for the Event.
         */
        @NotNull(message = "Capacity is required.")
        @Min(value = 1, message = "Capacity must be between 1 and 10000.")
        @Max(value = 10000, message = "Capacity must be between 1 and 10000.")
        private Integer capacity;

        /**
         * Optional starting lifecycle status; the service/entity applies its documented default and eligibility rules.
         */
        private Event.Status initialStatus;

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

        public Event.Status getInitialStatus() {
            return initialStatus;
        }

        public void setInitialStatus(Event.Status initialStatus) {
            this.initialStatus = initialStatus;
        }
    }

    /**
     * Safe event registration response DTO built from validated service results instead of serializing the entity.
     */
    public static class EventRegistrationResponse {

        /**
         * User account identifier associated with this response or relationship.
         */
        private final Long userId;
        /**
         * Optional shallow Event context or registered Event summary.
         */
        private final EventSummary event;

        /**
         * Packages the listed response fields supplied by the service without serializing a persistence entity.
         */
        public EventRegistrationResponse(Long userId, EventSummary event) {
            this.userId = userId;
            this.event = event;
        }

        public Long getUserId() {
            return userId;
        }

        public EventSummary getEvent() {
            return event;
        }
    }

    /**
     * Safe event response DTO built from validated service results instead of serializing the entity.
     */
    public static class EventResponse {

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
        private final Event.Status status;
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
                Event.Status status,
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
        public Event.Status getStatus() { return status; }
        public long getRegisteredCount() { return registeredCount; }
        public long getRemainingCapacity() { return remainingCapacity; }
        public long getTaskCount() { return taskCount; }
        public long getCompletedTaskCount() { return completedTaskCount; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
    }

    /**
     * Shallow event response DTO exposing only the listed fields, not a complete JPA relationship graph.
     */
    public static class EventSummary {

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
        private final Event.Status status;
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
                Event.Status status,
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
        public Event.Status getStatus() { return status; }
        public long getRemainingCapacity() { return remainingCapacity; }
    }

    /**
     * Writable update event JSON DTO, separate from the entity and returned fields.
     * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
     */
    public static class UpdateEventRequest {

        /**
         * Human-readable work or Event title.
         */
        @NotBlank(message = "Title is required.")
        @Size(min = 3, max = 120, message = "Title must be between 3 and 120 characters.")
        private String title;

        /**
         * Optional descriptive text; services normalize blank values where required.
         */
        @Size(max = 1000, message = "Description must not exceed 1000 characters.")
        private String description;

        /**
         * Server-local Event schedule used by registration and lifecycle eligibility.
         */
        @NotNull(message = "Event date and time are required.")
        private LocalDateTime eventDateTime;

        /**
         * Event location shown in details and registration summaries.
         */
        @NotBlank(message = "Location is required.")
        @Size(min = 2, max = 120, message = "Location must be between 2 and 120 characters.")
        private String location;

        /**
         * Maximum registration count allowed for the Event.
         */
        @NotNull(message = "Capacity is required.")
        @Min(value = 1, message = "Capacity must be between 1 and 10000.")
        @Max(value = 10000, message = "Capacity must be between 1 and 10000.")
        private Integer capacity;

        /**
         * Lifecycle enum value interpreted by this resource's service and transition rules.
         */
        @NotNull(message = "Status is required.")
        private Event.Status status;

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

        public Event.Status getStatus() {
            return status;
        }

        public void setStatus(Event.Status status) {
            this.status = status;
        }
    }
}

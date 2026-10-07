package com.rockey.hospitality.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Persists an Event's schedule, capacity, and lifecycle.
 * User owns registration membership; cancellation keeps registrations and linked preparation Tasks.
 */
// Marks a JPA-mapped database entity; the configured application validates the supplied schema instead of creating it.
@Entity
// Maps to the existing events table; @Index describes existing lookup indexes. These mappings do not create the application schema.
@Table(
        name = "events",
        indexes = @Index(
                name = "idx_events_status_event_date_time",
                columnList = "status,event_date_time"
        )
)
public class Event {

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    // Identifies the entity's primary-key field.
    @Id
    // Uses the database IDENTITY mechanism to generate the primary key.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Requires a supplied number to be greater than zero; null is handled separately.
    @Positive
    private Long id;

    /**
     * Human-readable work or Event title.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank
    // Checks supplied text length from 3 to 120 characters; required text is checked separately.
    @Size(min = 3, max = 120)
    // Maps this field to its matching SQL column (non-null, length 120) in the supplied schema.
    @Column(nullable = false, length = 120)
    private String title;

    /**
     * Optional descriptive text; services normalize blank values where required.
     */
    // Checks supplied text length up to 1000 characters; required text is checked separately.
    @Size(max = 1000)
    // Maps this field to its matching SQL column (length 1000) in the supplied schema.
    @Column(length = 1000)
    private String description;

    /**
     * Server-local Event schedule used by registration and lifecycle eligibility.
     */
    // Maps this field to event_date_time SQL column (non-null) in the supplied schema.
    @Column(name = "event_date_time", nullable = false)
    private LocalDateTime eventDateTime;

    /**
     * Event location shown in details and registration summaries.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank
    // Checks supplied text length from 2 to 120 characters; required text is checked separately.
    @Size(min = 2, max = 120)
    // Maps this field to its matching SQL column (non-null, length 120) in the supplied schema.
    @Column(nullable = false, length = 120)
    private String location;

    /**
     * Maximum registration count allowed for the Event.
     */
    // Checks that a supplied number is at least 1.
    @Min(1)
    // Checks that a supplied number is at most 10000.
    @Max(10000)
    // Maps this field to its matching SQL column (non-null) in the supplied schema.
    @Column(nullable = false)
    private Integer capacity;

    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    // Stores the enum's name as text, not its numeric ordinal.
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 20) in the supplied schema.
    @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.DRAFT;

    /**
     * Server-local creation timestamp retained for history.
     */
    // Maps this field to created_at SQL column (non-null, not rewritten by JPA updates) in the supplied schema.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Server-local timestamp of the latest persisted entity update.
     */
    // Maps this field to updated_at SQL column (non-null) in the supplied schema.
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Required no-argument constructor for Hibernate to instantiate persisted entities; application creation uses the explicit constructor.
     */
    protected Event() {
    }

    /**
     * Builds scheduled Event details and defaults an omitted status to DRAFT; EventService enforces creation eligibility.
     */
    public Event(
            String title,
            String description,
            LocalDateTime eventDateTime,
            String location,
            Integer capacity,
            EventStatus status
    ) {
        this.title = title;
        this.description = description;
        this.eventDateTime = eventDateTime;
        this.location = location;
        this.capacity = capacity;
        this.status = status == null ? EventStatus.DRAFT : status;
    }

    /**
     * Defaults a missing status to DRAFT and initializes creation/update timestamps from server-local time.
     */
    // Runs this lifecycle callback before the entity's first insert.
    @PrePersist
    void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = EventStatus.DRAFT;
        }
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Refreshes updatedAt using server-local time before Hibernate writes an entity update.
     */
    // Runs this lifecycle callback before a changed entity is written.
    @PreUpdate
    void prepareForUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Applies details and status that EventService has already validated against lifecycle and capacity rules.
     */
    public void update(
            String title,
            String description,
            LocalDateTime eventDateTime,
            String location,
            Integer capacity,
            EventStatus status
    ) {
        this.title = title;
        this.description = description;
        this.eventDateTime = eventDateTime;
        this.location = location;
        this.capacity = capacity;
        this.status = status;
    }

    /**
     * Marks the Event CANCELLED without removing registrations or linked preparation Tasks.
     */
    public void cancel() {
        status = EventStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getEventDateTime() {
        return eventDateTime;
    }

    public String getLocation() {
        return location;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public EventStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

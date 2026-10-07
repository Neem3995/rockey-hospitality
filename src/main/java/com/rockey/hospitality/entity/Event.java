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
 * STUDY NOTE: An Entity is a Java class JPA/Hibernate maps to stored database rows.
 * Here, @Entity marks this persistent class, while @Table selects the events MySQL table.
 * Event stores schedule, capacity and lifecycle for EventService and RegistrationService; User owns
 * registration membership.
 * schema.sql creates the tables; Hibernate validates their shape instead of creating them.
 */
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

    // Persistence study key:
    // @Index describes an existing lookup index; schema.sql, not these comments or mappings, creates it.
    // @Id marks the primary key; @GeneratedValue(IDENTITY) lets MySQL generate it on insertion.
    // @Column maps a Java field to a SQL column; nullable/length settings describe the supplied schema.
    // @Enumerated(STRING) stores enum names such as DRAFT, not positions such as 0 or 1.
    // @PrePersist runs before the first insert; the callback supplies lifecycle defaults and timestamps.
    // @PreUpdate runs before an entity update; the callback refreshes its update timestamp.
    // Time study key: callbacks use server-local LocalDateTime; Hibernate's JDBC time-zone setting is UTC.
    // MySQL DATETIME has no zone label; do not silently treat every operational API LocalDateTime as UTC.
    // Validation study key (the numbers/patterns are specified on each annotated field):
    // @NotBlank requires non-null text containing at least one non-whitespace character.
    // @Size checks length/count against the declared min/max (text length for the fields here).
    // @Positive checks that a supplied number is greater than zero.
    // @Min sets an inclusive minimum for a supplied number.
    // @Max sets an inclusive maximum for a supplied number.
    // Most shape/range validators accept null; @NotNull or @NotBlank supplies required-value checks.

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Positive
    private Long id;

    /**
     * Human-readable work or Event title.
     */
    @NotBlank
    @Size(min = 3, max = 120)
    // Maps this field to its matching SQL column (non-null, length 120) in the supplied schema.
    @Column(nullable = false, length = 120)
    private String title;

    /**
     * Optional descriptive text; services normalize blank values where required.
     */
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
    @NotBlank
    @Size(min = 2, max = 120)
    // Maps this field to its matching SQL column (non-null, length 120) in the supplied schema.
    @Column(nullable = false, length = 120)
    private String location;

    /**
     * Maximum registration count allowed for the Event.
     */
    @Min(1)
    @Max(10000)
    // Maps this field to its matching SQL column (non-null) in the supplied schema.
    @Column(nullable = false)
    private Integer capacity;

    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 20) in the supplied schema.
    @Column(nullable = false, length = 20)
    private Event.Status status = Event.Status.DRAFT;

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
            Event.Status status
    ) {
        this.title = title;
        this.description = description;
        this.eventDateTime = eventDateTime;
        this.location = location;
        this.capacity = capacity;
        this.status = status == null ? Event.Status.DRAFT : status;
    }

    /**
     * Defaults a missing status to DRAFT and initializes creation/update timestamps from server-local time.
     */
    @PrePersist
    void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = Event.Status.DRAFT;
        }
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Refreshes updatedAt using server-local time before Hibernate writes an entity update.
     */
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
            Event.Status status
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
        status = Event.Status.CANCELLED;
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

    public Event.Status getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }


    /**
     * Event.Status is an enum: a Java type limited to a fixed set of valid choices.
     * Defines Event lifecycle from DRAFT through OPEN, CLOSED, and IN_PROGRESS to terminal COMPLETED or CANCELLED.
     * EventService enforces permitted transitions; CLOSED stops registration.
     */
    public enum Status {
        /**
         * Event being prepared; self-registration is not open.
         */
        DRAFT,
        /**
         * Upcoming Event accepting eligible registrations within capacity.
         */
        OPEN,
        /**
         * Registration closed before the Event starts.
         */
        CLOSED,
        /**
         * Event underway; allowed completion follows the transition map.
         */
        IN_PROGRESS,
        /**
         * Terminal finished Event retaining registrations and Tasks.
         */
        COMPLETED,
        /**
         * Terminal cancelled Event retaining registrations and Tasks.
         */
        CANCELLED
    }
}

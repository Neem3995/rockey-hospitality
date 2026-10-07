package com.rockey.hospitality.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Persists Room identity, turnover status, and optional next-arrival readiness time.
 * This entity represents operations, not reservations or bookings.
 */
// Marks a JPA-mapped database entity; the configured application validates the supplied schema instead of creating it.
@Entity
// Maps to the existing rooms table; @Index describes existing lookup indexes and @UniqueConstraint describes unique keys. These mappings do not create the application schema.
@Table(
        name = "rooms",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rooms_room_number",
                columnNames = "room_number"
        ),
        indexes = @Index(
                name = "idx_rooms_status_next_arrival",
                columnList = "status,next_arrival_at"
        )
)
public class Room {
    // Tasks reference this Room; soft deactivation preserves their hotel context.

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
     * Unique operational Room identifier; it is not a database ID or a booking.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank
    // Checks supplied text length up to 10 characters; required text is checked separately.
    @Size(max = 10)
    // Maps this field to room_number SQL column (non-null, length 10) in the supplied schema.
    @Column(name = "room_number", nullable = false, length = 10)
    private String roomNumber;

    /**
     * Operational Room classification used in display and type filtering.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank
    // Checks supplied text length from 2 to 50 characters; required text is checked separately.
    @Size(min = 2, max = 50)
    // Maps this field to room_type SQL column (non-null, length 50) in the supplied schema.
    @Column(name = "room_type", nullable = false, length = 50)
    private String roomType;

    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    // Stores the enum's name as text, not its numeric ordinal.
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 30) in the supplied schema.
    @Column(nullable = false, length = 30)
    private RoomStatus status = RoomStatus.READY;

    /**
     * Room floor number (1-99), stored as SMALLINT and used for display and floor filtering.
     */
    // Checks that a supplied number is at least 1.
    @Min(1)
    // Checks that a supplied number is at most 99.
    @Max(99)
    // Maps this field to its matching SQL column (non-null) in the supplied schema.
    @Column(nullable = false)
    // Maps this Java Integer using SQL SMALLINT to match the supplied Room floor column.
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer floor;

    /**
     * Optional server-local arrival readiness time, not a Reservation or Booking relationship.
     */
    // Maps this field to next_arrival_at SQL column in the supplied schema.
    @Column(name = "next_arrival_at")
    private LocalDateTime nextArrivalAt;

    /**
     * Soft-lifecycle flag; inactive rows retain their identity and history.
     */
    // Maps this field to its matching SQL column (non-null) in the supplied schema.
    @Column(nullable = false)
    private Boolean active = true;

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
    protected Room() {
    }

    /**
     * Builds Room details and defaults an omitted status to READY; RoomService validates identity and arrival time first.
     */
    public Room(
            String roomNumber,
            String roomType,
            Integer floor,
            RoomStatus status,
            LocalDateTime nextArrivalAt
    ) {
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.floor = floor;
        this.status = status == null ? RoomStatus.READY : status;
        this.nextArrivalAt = nextArrivalAt;
    }

    /**
     * Defaults missing status/active values and initializes creation/update timestamps from server-local time.
     */
    // Runs this lifecycle callback before the entity's first insert.
    @PrePersist
    void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = RoomStatus.READY;
        }
        if (active == null) {
            active = true;
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
     * Applies Room details and active state after service validation; turnover status changes use updateStatus.
     */
    public void updateDetails(
            String roomType,
            Integer floor,
            LocalDateTime nextArrivalAt,
            Boolean active
    ) {
        this.roomType = roomType;
        this.floor = floor;
        this.nextArrivalAt = nextArrivalAt;
        this.active = active;
    }

    /**
     * Stores a status already checked against RoomService's transition rules.
     */
    public void updateStatus(RoomStatus status) {
        this.status = status;
    }

    /**
     * Marks the Room inactive without deleting arrival context or Task references.
     */
    public void deactivate() {
        active = false;
    }

    public Long getId() {
        return id;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public String getRoomType() {
        return roomType;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public Integer getFloor() {
        return floor;
    }

    public LocalDateTime getNextArrivalAt() {
        return nextArrivalAt;
    }

    public Boolean getActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

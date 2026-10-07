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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * STUDY NOTE: An Entity is a Java class JPA/Hibernate maps to stored database rows.
 * Here, @Entity marks this persistent class, while @Table selects the rooms MySQL table.
 * Room stores operational readiness and turnover state for RoomService and Tasks; it is not a reservation
 * or booking.
 * schema.sql creates the tables; Hibernate validates their shape instead of creating them.
 */
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

    // Persistence study key:
    // @Index describes an existing lookup index; schema.sql, not these comments or mappings, creates it.
    // @UniqueConstraint describes a unique key so duplicate field values or join pairs cannot be stored.
    // @Id marks the primary key; @GeneratedValue(IDENTITY) lets MySQL generate it on insertion.
    // @Column maps a Java field to a SQL column; nullable/length settings describe the supplied schema.
    // @Enumerated(STRING) stores enum names such as READY, not positions such as 0 or 1.
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
    // Tasks reference this Room; soft deactivation preserves their hotel context.

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Positive
    private Long id;

    /**
     * Unique operational Room identifier; it is not a database ID or a booking.
     */
    @NotBlank
    @Size(max = 10)
    // Maps this field to room_number SQL column (non-null, length 10) in the supplied schema.
    @Column(name = "room_number", nullable = false, length = 10)
    private String roomNumber;

    /**
     * Operational Room classification used in display and type filtering.
     */
    @NotBlank
    @Size(min = 2, max = 50)
    // Maps this field to room_type SQL column (non-null, length 50) in the supplied schema.
    @Column(name = "room_type", nullable = false, length = 50)
    private String roomType;

    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 30) in the supplied schema.
    @Column(nullable = false, length = 30)
    private Room.Status status = Room.Status.READY;

    /**
     * Room floor number (1-99), stored as SMALLINT and used for display and floor filtering.
     */
    @Min(1)
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
            Room.Status status,
            LocalDateTime nextArrivalAt
    ) {
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.floor = floor;
        this.status = status == null ? Room.Status.READY : status;
        this.nextArrivalAt = nextArrivalAt;
    }

    /**
     * Defaults missing status/active values and initializes creation/update timestamps from server-local time.
     */
    @PrePersist
    void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = Room.Status.READY;
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
    public void updateStatus(Room.Status status) {
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

    public Room.Status getStatus() {
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


    /**
     * Room.Status is an enum: a Java type limited to a fixed set of valid choices.
     * Describes readiness and turnover states, plus MAINTENANCE and OUT_OF_SERVICE.
     * RoomService's transition map enforces the permitted route back through inspection to READY.
     */
    public enum Status {
        /**
         * Operationally ready Room.
         */
        READY,
        /**
         * Room currently occupied, not a separate booking model.
         */
        OCCUPIED,
        /**
         * Room awaiting cleaning.
         */
        DIRTY,
        /**
         * Room undergoing cleaning.
         */
        CLEANING,
        /**
         * Room awaiting readiness inspection.
         */
        INSPECTION,
        /**
         * Room requiring maintenance before readiness.
         */
        MAINTENANCE,
        /**
         * Room unavailable for operational use; distinct from row deactivation.
         */
        OUT_OF_SERVICE
    }
}

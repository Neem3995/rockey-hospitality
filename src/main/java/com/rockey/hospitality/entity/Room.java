package com.rockey.hospitality.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * STUDY NOTE: An entity is a Java object mapped to a stored database row; this one maps the rooms table.
 * @Entity/@Table select the table, @Id/@GeneratedValue use the MySQL-generated key, @Column describes fields,
 * and @Enumerated(STRING) stores status names such as DIRTY rather than numeric positions.
 * Status follows the housekeeping cycle READY, DIRTY, CLEANING, INSPECTION or OUT_OF_SERVICE.
 * RoomService and TaskService decide which transitions are allowed. Deactivation keeps the row and its history.
 * @PrePersist/@PreUpdate stamp server-local timestamps; schema.sql creates the table and Hibernate validates it.
 */
@Entity
@Table(name = "rooms")
public class Room {
    public enum Status { READY, DIRTY, CLEANING, INSPECTION, OUT_OF_SERVICE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "room_number", nullable = false, unique = true, length = 10)
    private String roomNumber;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.SMALLINT)
    @Column(nullable = false)
    private Integer floor;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Status status = Status.READY;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Hibernate reconstructs database rows through this empty constructor. */
    protected Room() { }
    /** Initial room state is validated by RoomService, not by this constructor. */
    public Room(String roomNumber, Integer floor, Status status) {
        this.roomNumber = roomNumber; this.floor = floor;
        this.status = status == null ? Status.READY : status;
    }
    @PrePersist
    void prepareForInsert() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate
    void prepareForUpdate() { updatedAt = LocalDateTime.now(); }
    /** Room numbers remain stable; active work guards are checked before changing this flag. */
    public void update(Integer floor, boolean active) { this.floor = floor; this.active = active; }
    /** Task work and inspections call this only after checking the permitted transition. */
    public void updateStatus(Status status) { this.status = status; }
    public void deactivate() { active = false; }
    public Long getId() { return id; }
    public String getRoomNumber() { return roomNumber; }
    public Integer getFloor() { return floor; }
    public Status getStatus() { return status; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

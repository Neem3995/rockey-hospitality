package com.rockey.hospitality.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * STUDY NOTE: Room is the class; a Room instance holds the values for one rooms-table row.
 * The services create/change instances, and Hibernate uses these annotations when reading or saving.
 * First @Id identifies the key; @GeneratedValue uses MySQL's generated id. Status is stored as text.
 * RoomService and TaskService decide allowed moves before calling updateStatus; this entity
 * does not check the caller's role. deactivate keeps the row for task and inspection history.
 * The callbacks set local timestamps with no stored offset. database/schema.sql creates the
 * table; Hibernate validates the mapping rather than building or migrating the schema.
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
    // STRING stores READY/DIRTY/etc., not enum positions. The field is this instance's current value;
    // RoomService/TaskService check transitions before changing it, and Hibernate persists it later.
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

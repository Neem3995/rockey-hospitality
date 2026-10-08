package com.rockey.hospitality.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * STUDY NOTE: One Inspection instance represents one recorded inspections-table result.
 * RoomService constructs it after checking the latest completed task and authenticated supervisor.
 * Three many-to-one mappings store the room, task and inspector foreign keys; result stores PASS/FAIL.
 * Saving the record and changing the room share a transaction. Existing records remain history;
 * there is no inspection edit/delete API. This entity does not authorize or schedule inspections.
 * database/schema.sql creates the table; Hibernate uses and validates this mapping.
 */
@Entity
@Table(name = "inspections")
public class Inspection {
    public enum Result { PASS, FAIL }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Each history entry points to one room, completed task and supervising user.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspected_by_user_id", nullable = false)
    private User inspectedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Result result;
    @Column(length = 1000)
    private String notes;
    @Column(name = "inspected_at", nullable = false, updatable = false)
    private LocalDateTime inspectedAt;

    protected Inspection() { }
    /** Immutable inspection history; the supervisor identity comes from authentication. */
    public Inspection(Room room, Task task, User inspectedBy, Result result, String notes, LocalDateTime time) {
        this.room = room; this.task = task; this.inspectedBy = inspectedBy;
        this.result = result; this.notes = notes; this.inspectedAt = time;
    }
    public Long getId() { return id; }
    public Room getRoom() { return room; }
    public Task getTask() { return task; }
    public User getInspectedBy() { return inspectedBy; }
    public Result getResult() { return result; }
    public String getNotes() { return notes; }
    public LocalDateTime getInspectedAt() { return inspectedAt; }
}

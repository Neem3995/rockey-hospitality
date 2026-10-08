package com.rockey.hospitality.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * STUDY NOTE: An entity is a Java object mapped to a stored database row; this one maps the inspections table.
 * @ManyToOne/@JoinColumn link each record to its Room, completed Task and inspecting supervisor User.
 * @Enumerated(STRING) stores PASS or FAIL as text.
 * Records are append-only history. RoomService sets inspectedAt and changes the room to READY on PASS
 * or DIRTY on FAIL. schema.sql creates the table and Hibernate only validates this mapping.
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

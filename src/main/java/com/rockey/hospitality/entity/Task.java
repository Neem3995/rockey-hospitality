package com.rockey.hospitality.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * STUDY NOTE: Task is the class for one tasks-table row, with one assigned User and one Room.
 * TaskService constructs or changes an instance after checking ownership, eligibility and lifecycle.
 * JPA maps the foreign keys and text enums. start/complete/cancel change this object's state;
 * TaskService coordinates the matching room change and decides whether the action is allowed.
 * The callbacks stamp local times. Terminal tasks stay as history, not deleted records.
 * database/schema.sql creates the table; this mapping is not a migration or an authorization check.
 */
@Entity
@Table(name = "tasks")
public class Task {
    public enum Status { ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED }
    public enum Priority { LOW, MEDIUM, HIGH, URGENT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150)
    private String title;
    @Column(length = 1000)
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.ASSIGNED;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Priority priority;
    // Many tasks reference one user/room; LAZY reads and DTO mapping happen inside service transactions.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_user_id", nullable = false)
    private User assignedUser;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
    @Column(name = "due_at")
    private LocalDateTime dueAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Task() { }
    /** Normal work always has an active USER and a dirty active room. */
    public Task(String title, String description, Priority priority, User assignedUser, Room room, LocalDateTime dueAt) {
        this.title = title; this.description = description; this.priority = priority;
        this.assignedUser = assignedUser; this.room = room; this.dueAt = dueAt;
    }
    @PrePersist
    void prepareForInsert() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate
    void prepareForUpdate() { updatedAt = LocalDateTime.now(); }
    /** Editing/reassignment preserves the original room and task history. */
    public void update(String title, String description, Priority priority, User assignedUser, LocalDateTime dueAt) {
        this.title = title; this.description = description; this.priority = priority;
        this.assignedUser = assignedUser; this.dueAt = dueAt;
    }
    /** Only TaskService can decide when these lifecycle changes are allowed. */
    public void start() { status = Status.IN_PROGRESS; }
    public void complete(LocalDateTime time) { status = Status.COMPLETED; completedAt = time; }
    public void cancel() { status = Status.CANCELLED; }
    public boolean isTerminal() { return status == Status.COMPLETED || status == Status.CANCELLED; }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Status getStatus() { return status; }
    public Priority getPriority() { return priority; }
    public User getAssignedUser() { return assignedUser; }
    public Room getRoom() { return room; }
    public LocalDateTime getDueAt() { return dueAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

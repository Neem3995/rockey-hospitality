package com.rockey.hospitality.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "tasks",
        indexes = {
                @Index(
                        name = "idx_tasks_department_status",
                        columnList = "department_id,status"
                ),
                @Index(
                        name = "idx_tasks_assigned_employee",
                        columnList = "assigned_employee_id"
                ),
                @Index(name = "idx_tasks_room", columnList = "room_id"),
                @Index(name = "idx_tasks_event", columnList = "event_id"),
                @Index(name = "idx_tasks_due_at", columnList = "due_at")
        }
)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Positive
    private Long id;

    @NotBlank
    @Size(min = 3, max = 120)
    @Column(nullable = false, length = 120)
    private String title;

    @Size(max = 1000)
    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_employee_id")
    private Employee assignedEmployee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Task() {
    }

    public Task(
            String title,
            String description,
            Department department,
            Employee assignedEmployee,
            Room room,
            Event event,
            TaskPriority priority,
            LocalDateTime dueAt
    ) {
        this.title = title;
        this.description = description;
        this.department = department;
        this.assignedEmployee = assignedEmployee;
        this.room = room;
        this.event = event;
        this.priority = priority == null ? TaskPriority.MEDIUM : priority;
        this.dueAt = dueAt;
        this.status = assignedEmployee == null ? TaskStatus.OPEN : TaskStatus.ASSIGNED;
    }

    public Task(
            String title,
            String description,
            Department department,
            Employee assignedEmployee,
            Room room,
            TaskPriority priority,
            LocalDateTime dueAt
    ) {
        this(
                title,
                description,
                department,
                assignedEmployee,
                room,
                null,
                priority,
                dueAt
        );
    }

    @PrePersist
    void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = assignedEmployee == null ? TaskStatus.OPEN : TaskStatus.ASSIGNED;
        }
        if (priority == null) {
            priority = TaskPriority.MEDIUM;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void prepareForUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void update(
            String title,
            String description,
            Department department,
            Employee assignedEmployee,
            Room room,
            Event event,
            TaskPriority priority,
            TaskStatus status,
            LocalDateTime dueAt,
            LocalDateTime completedAt
    ) {
        this.title = title;
        this.description = description;
        this.department = department;
        this.assignedEmployee = assignedEmployee;
        this.room = room;
        this.event = event;
        this.priority = priority;
        this.status = status;
        this.dueAt = dueAt;
        this.completedAt = completedAt;
    }

    public void assign(Employee employee) {
        assignedEmployee = employee;
        if (employee == null && status == TaskStatus.ASSIGNED) {
            status = TaskStatus.OPEN;
        } else if (employee != null && status == TaskStatus.OPEN) {
            status = TaskStatus.ASSIGNED;
        }
    }

    public void complete(LocalDateTime completionTime) {
        status = TaskStatus.COMPLETED;
        completedAt = completionTime;
    }

    public void cancel() {
        status = TaskStatus.CANCELLED;
        completedAt = null;
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

    public TaskStatus getStatus() {
        return status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public Department getDepartment() {
        return department;
    }

    public Employee getAssignedEmployee() {
        return assignedEmployee;
    }

    public Room getRoom() {
        return room;
    }

    public Event getEvent() {
        return event;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

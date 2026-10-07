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

/**
 * Persists operational work with a required Department and optional assignee, Room, and Event.
 * Terminal lifecycle changes preserve references instead of deleting the row.
 */
// Marks a JPA-mapped database entity; the configured application validates the supplied schema instead of creating it.
@Entity
// Maps to the existing tasks table; @Index describes existing lookup indexes. These mappings do not create the application schema.
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
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    // Stores the enum's name as text, not its numeric ordinal.
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 20) in the supplied schema.
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.OPEN;

    /**
     * Task urgency enum; the service/entity defaults omitted creation priority to MEDIUM, while updates require a value.
     */
    // Stores the enum's name as text, not its numeric ordinal.
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 20) in the supplied schema.
    @Column(nullable = false, length = 20)
    private TaskPriority priority = TaskPriority.MEDIUM;

    /**
     * Required owning Department for the work; service checks require an active destination.
     */
    // Many rows can reference the same related entity; LAZY loads the relationship when it is needed within the service transaction.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    // Department is required; assignee, Room and Event are optional work context below.
    // Stores this relationship's foreign key in department_id, which must be present.
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    /**
     * Optional assignee whose linked User determines STAFF ownership; terminal work retains this history.
     */
    // Many rows can reference the same related entity; LAZY loads the relationship when it is needed within the service transaction.
    @ManyToOne(fetch = FetchType.LAZY)
    // Stores this relationship's foreign key in assigned_employee_id, which may be null.
    @JoinColumn(name = "assigned_employee_id")
    private Employee assignedEmployee;

    /**
     * Optional Room context; new references require an active Room.
     */
    // Many rows can reference the same related entity; LAZY loads the relationship when it is needed within the service transaction.
    @ManyToOne(fetch = FetchType.LAZY)
    // Stores this relationship's foreign key in room_id, which may be null.
    @JoinColumn(name = "room_id")
    private Room room;

    /**
     * Optional Event preparation context; cancelled Events cannot be assigned to new or edited Tasks.
     */
    // Many rows can reference the same related entity; LAZY loads the relationship when it is needed within the service transaction.
    @ManyToOne(fetch = FetchType.LAZY)
    // Stores this relationship's foreign key in event_id, which may be null.
    @JoinColumn(name = "event_id")
    private Event event;

    /**
     * Server-local creation timestamp retained for history.
     */
    // Maps this field to created_at SQL column (non-null, not rewritten by JPA updates) in the supplied schema.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Optional server-local due time; only non-terminal work strictly before now is overdue.
     */
    // Maps this field to due_at SQL column in the supplied schema.
    @Column(name = "due_at")
    private LocalDateTime dueAt;

    /**
     * Completion timestamp for COMPLETED work, distinct from cancellation.
     */
    // Maps this field to completed_at SQL column in the supplied schema.
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Server-local timestamp of the latest persisted entity update.
     */
    // Maps this field to updated_at SQL column (non-null) in the supplied schema.
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Required no-argument constructor for Hibernate to instantiate persisted entities; application creation uses the explicit constructor.
     */
    protected Task() {
    }

    /**
     * Builds Task state with default MEDIUM priority and OPEN/ASSIGNED status based on the optional assignee.
     * TaskService validates related records before construction.
     */
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

    /**
     * Delegates creation without Event context to the main Task constructor, preserving the same defaults.
     */
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

    /**
     * Defaults status from assignment and priority to MEDIUM and initializes creation/update timestamps from server-local time.
     */
    // Runs this lifecycle callback before the entity's first insert.
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

    /**
     * Refreshes updatedAt using server-local time before Hibernate writes an entity update.
     */
    // Runs this lifecycle callback before a changed entity is written.
    @PreUpdate
    void prepareForUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Applies the validated Task state and references together, including its completion timestamp.
     */
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

    /**
     * Changes the assignee and switches OPEN↔ASSIGNED when necessary.
     * TaskService separately prevents illegal assignment or unassignment.
     */
    public void assign(Employee employee) {
        assignedEmployee = employee;
        if (employee == null && status == TaskStatus.ASSIGNED) {
            status = TaskStatus.OPEN;
        } else if (employee != null && status == TaskStatus.OPEN) {
            status = TaskStatus.ASSIGNED;
        }
    }

    /**
     * Records COMPLETED and the supplied completion time after service access and transition checks.
     */
    public void complete(LocalDateTime completionTime) {
        status = TaskStatus.COMPLETED;
        completedAt = completionTime;
    }

    /**
     * Records CANCELLED, clears completion time, and leaves relationship history intact.
     */
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

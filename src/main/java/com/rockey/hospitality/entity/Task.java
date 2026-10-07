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
 * STUDY NOTE: An Entity is a Java class JPA/Hibernate maps to stored database rows.
 * Here, @Entity marks this persistent class, while @Table selects the tasks MySQL table.
 * Task stores work for TaskService with a required Department and optional Employee, Room and Event
 * references.
 * schema.sql creates the tables; Hibernate validates their shape instead of creating them.
 */
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

    // Persistence study key:
    // @JoinColumn names a foreign-key column linking this row to another table's primary key.
    // @Id marks the primary key; @GeneratedValue(IDENTITY) lets MySQL generate it on insertion.
    // @Column maps a Java field to a SQL column; nullable/length settings describe the supplied schema.
    // @Enumerated(STRING) stores enum names such as OPEN, not positions such as 0 or 1.
    // @PrePersist runs before the first insert; the callback supplies lifecycle defaults and timestamps.
    // @PreUpdate runs before an entity update; the callback refreshes its update timestamp.
    // @Index describes an existing lookup index; schema.sql, not these comments or mappings, creates it.
    // Time study key: callbacks use server-local LocalDateTime; Hibernate's JDBC time-zone setting is UTC.
    // MySQL DATETIME has no zone label; do not silently treat every operational API LocalDateTime as UTC.
    // Validation study key (the numbers/patterns are specified on each annotated field):
    // @NotBlank requires non-null text containing at least one non-whitespace character.
    // @Size checks length/count against the declared min/max (text length for the fields here).
    // @Positive checks that a supplied number is greater than zero.
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
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 20) in the supplied schema.
    @Column(nullable = false, length = 20)
    private Task.Status status = Task.Status.OPEN;

    /**
     * Task urgency enum; the service/entity defaults omitted creation priority to MEDIUM, while updates require a value.
     */
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 20) in the supplied schema.
    @Column(nullable = false, length = 20)
    private Task.Priority priority = Task.Priority.MEDIUM;

    /**
     * Required owning Department for the work; service checks require an active destination.
     */
    // @ManyToOne lets many Task rows reference the same Department; LAZY defers loading it until needed.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    // Department is required; assignee, Room and Event are optional work context below.
    // Stores this relationship's foreign key in department_id, which must be present.
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    /**
     * Optional assignee whose linked User determines STAFF ownership; terminal work retains this history.
     */
    // @ManyToOne lets many Task rows reference the same Employee; LAZY defers loading it until needed.
    @ManyToOne(fetch = FetchType.LAZY)
    // Stores this relationship's foreign key in assigned_employee_id, which may be null.
    @JoinColumn(name = "assigned_employee_id")
    private Employee assignedEmployee;

    /**
     * Optional Room context; new references require an active Room.
     */
    // @ManyToOne lets many Task rows reference the same Room; LAZY defers loading it until needed.
    @ManyToOne(fetch = FetchType.LAZY)
    // Stores this relationship's foreign key in room_id, which may be null.
    @JoinColumn(name = "room_id")
    private Room room;

    /**
     * Optional Event preparation context; cancelled Events cannot be assigned to new or edited Tasks.
     */
    // @ManyToOne lets many Task rows reference the same Event; LAZY defers loading it until needed.
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
            Task.Priority priority,
            LocalDateTime dueAt
    ) {
        this.title = title;
        this.description = description;
        this.department = department;
        this.assignedEmployee = assignedEmployee;
        this.room = room;
        this.event = event;
        this.priority = priority == null ? Task.Priority.MEDIUM : priority;
        this.dueAt = dueAt;
        this.status = assignedEmployee == null ? Task.Status.OPEN : Task.Status.ASSIGNED;
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
            Task.Priority priority,
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
    @PrePersist
    void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = assignedEmployee == null ? Task.Status.OPEN : Task.Status.ASSIGNED;
        }
        if (priority == null) {
            priority = Task.Priority.MEDIUM;
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
     * Applies the validated Task state and references together, including its completion timestamp.
     */
    public void update(
            String title,
            String description,
            Department department,
            Employee assignedEmployee,
            Room room,
            Event event,
            Task.Priority priority,
            Task.Status status,
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
        if (employee == null && status == Task.Status.ASSIGNED) {
            status = Task.Status.OPEN;
        } else if (employee != null && status == Task.Status.OPEN) {
            status = Task.Status.ASSIGNED;
        }
    }

    /**
     * Records COMPLETED and the supplied completion time after service access and transition checks.
     */
    public void complete(LocalDateTime completionTime) {
        status = Task.Status.COMPLETED;
        completedAt = completionTime;
    }

    /**
     * Records CANCELLED, clears completion time, and leaves relationship history intact.
     */
    public void cancel() {
        status = Task.Status.CANCELLED;
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

    public Task.Status getStatus() {
        return status;
    }

    public Task.Priority getPriority() {
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


    /**
     * Task.Status is an enum: a Java type limited to a fixed set of valid choices.
     * Tracks OPEN, ASSIGNED, and IN_PROGRESS work, with COMPLETED and CANCELLED as terminal history.
     * TaskService enforces transitions and assignee requirements.
     */
    public enum Status {
        /**
         * Unassigned, non-terminal work.
         */
        OPEN,
        /**
         * Non-terminal work with an assignee.
         */
        ASSIGNED,
        /**
         * Assigned work currently underway.
         */
        IN_PROGRESS,
        /**
         * Terminal work with completion time and retained assignee history.
         */
        COMPLETED,
        /**
         * Terminal cancelled work retaining references without a completion time.
         */
        CANCELLED
    }

    /**
     * Task.Priority is an enum: a Java type limited to a fixed set of valid choices.
     * Labels work urgency as LOW, MEDIUM, HIGH, or URGENT.
     * MEDIUM is the default; HIGH and URGENT are used by Task alert automation.
     */
    public enum Priority {
        /**
         * Lower-priority work.
         */
        LOW,
        /**
         * Default work priority.
         */
        MEDIUM,
        /**
         * High-priority work included in Task alert conditions.
         */
        HIGH,
        /**
         * Urgent work included in Task alert conditions.
         */
        URGENT
    }
}

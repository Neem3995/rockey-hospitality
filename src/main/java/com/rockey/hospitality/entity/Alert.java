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
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Persists one employee-addressed alert with optional Task context and a stable source key.
 * Read and resolution timestamps preserve its lifecycle history.
 */
// Marks a JPA-mapped database entity; the configured application validates the supplied schema instead of creating it.
@Entity
// Maps to the existing alerts table; @Index describes existing lookup indexes. These mappings do not create the application schema.
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alerts_employee_status_created", columnList = "employee_id,status,created_at"),
        @Index(name = "idx_alerts_employee_type_status_source", columnList = "employee_id,type,status,source_key"),
        @Index(name = "idx_alerts_task", columnList = "task_id")
})
public class Alert {

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
     * Alert source category used by filtering and automated reconciliation.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull
    // Stores the enum's name as text, not its numeric ordinal.
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 20) in the supplied schema.
    @Column(nullable = false, length = 20)
    private AlertType type = AlertType.SYSTEM;

    /**
     * Safe client-facing explanatory text without private authentication or database details.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank
    // Checks supplied text length from 3 to 500 characters; required text is checked separately.
    @Size(min = 3, max = 500)
    // Maps this field to its matching SQL column (non-null, length 500) in the supplied schema.
    @Column(nullable = false, length = 500)
    private String message;

    /**
     * Alert urgency enum; distinct from its source type and read/resolved state.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull
    // Stores the enum's name as text, not its numeric ordinal.
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 20) in the supplied schema.
    @Column(nullable = false, length = 20)
    private AlertSeverity severity = AlertSeverity.INFO;

    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull
    // Stores the enum's name as text, not its numeric ordinal.
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 20) in the supplied schema.
    @Column(nullable = false, length = 20)
    private AlertStatus status = AlertStatus.UNREAD;

    /**
     * Required recipient Employee used for ownership checks and serialized alert reconciliation.
     */
    // Many rows can reference the same related entity; LAZY loads the relationship when it is needed within the service transaction.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    // An Employee owns the notice; sourceKey correlates generated Room/Task/Inventory conditions.
    // Stores this relationship's foreign key in employee_id, which must be present.
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /**
     * Optional Task context; Room and Inventory alerts can have no Task reference.
     */
    // Many rows can reference the same related entity; LAZY loads the relationship when it is needed within the service transaction.
    @ManyToOne(fetch = FetchType.LAZY)
    // Stores this relationship's foreign key in task_id, which may be null.
    @JoinColumn(name = "task_id")
    private Task task;

    /**
     * Stable source-condition identifier used to suppress equivalent unresolved alerts.
     */
    // Checks supplied text length up to 120 characters; required text is checked separately.
    @Size(max = 120)
    // Maps this field to source_key SQL column (length 120) in the supplied schema.
    @Column(name = "source_key", length = 120)
    private String sourceKey;

    /**
     * Server-local creation timestamp retained for history.
     */
    // Maps this field to created_at SQL column (non-null, not rewritten by JPA updates) in the supplied schema.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * First acknowledgement time; null means it has not been recorded.
     */
    // Maps this field to read_at SQL column in the supplied schema.
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /**
     * First resolution time; history is preserved rather than deleted.
     */
    // Maps this field to resolved_at SQL column in the supplied schema.
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * Required no-argument constructor for Hibernate to instantiate persisted entities; application creation uses the explicit constructor.
     */
    protected Alert() {
    }

    /**
     * Builds an alert with recipient, optional Task, source key, and supplied creation time, trimming its message.
     */
    public Alert(AlertType type, String message, Employee employee, Task task,
                 String sourceKey, LocalDateTime createdAt) {
        this.type = type;
        this.message = message.trim();
        this.employee = employee;
        this.task = task;
        this.sourceKey = sourceKey;
        this.createdAt = createdAt;
    }

    /**
     * Sets READ and records the first read time; later calls do not overwrite that timestamp.
     * Service validation decides whether this transition is allowed.
     */
    public void markRead(LocalDateTime now) {
        status = AlertStatus.READ;
        if (readAt == null) readAt = now;
    }

    /**
     * Sets RESOLVED and fills missing read and resolution times while preserving earlier timestamps.
     */
    public void resolve(LocalDateTime now) {
        status = AlertStatus.RESOLVED;
        if (readAt == null) readAt = now;
        if (resolvedAt == null) resolvedAt = now;
    }

    public Long getId() { return id; }
    public AlertType getType() { return type; }
    public String getMessage() { return message; }
    public AlertSeverity getSeverity() { return severity; }
    public AlertStatus getStatus() { return status; }
    public Employee getEmployee() { return employee; }
    public Task getTask() { return task; }
    public String getSourceKey() { return sourceKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReadAt() { return readAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}

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
 * STUDY NOTE: An Entity is a Java class JPA/Hibernate maps to stored database rows.
 * Here, @Entity marks this persistent class, while @Table selects the alerts MySQL table.
 * Alert stores one recipient Employee, optional Task context and source/lifecycle history used by
 * AlertService and automation.
 * schema.sql creates the tables; Hibernate validates their shape instead of creating them.
 */
@Entity
// Maps to the existing alerts table; @Index describes existing lookup indexes. These mappings do not create the application schema.
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alerts_employee_status_created", columnList = "employee_id,status,created_at"),
        @Index(name = "idx_alerts_employee_type_status_source", columnList = "employee_id,type,status,source_key"),
        @Index(name = "idx_alerts_task", columnList = "task_id")
})
public class Alert {

    // Persistence study key:
    // @JoinColumn names a foreign-key column linking this row to another table's primary key.
    // @Id marks the primary key; @GeneratedValue(IDENTITY) lets MySQL generate it on insertion.
    // @Column maps a Java field to a SQL column; nullable/length settings describe the supplied schema.
    // @Enumerated(STRING) stores enum names such as UNREAD, not positions such as 0 or 1.
    // @Index describes an existing lookup index; schema.sql, not these comments or mappings, creates it.
    // Time study key: callbacks use server-local LocalDateTime; Hibernate's JDBC time-zone setting is UTC.
    // MySQL DATETIME has no zone label; do not silently treat every operational API LocalDateTime as UTC.
    // Validation study key (the numbers/patterns are specified on each annotated field):
    // @NotBlank requires non-null text containing at least one non-whitespace character.
    // @NotNull requires a value; it does not check text length or a numeric range.
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
     * Alert source category used by filtering and automated reconciliation.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 20) in the supplied schema.
    @Column(nullable = false, length = 20)
    private Alert.Type type = Alert.Type.SYSTEM;

    /**
     * Safe client-facing explanatory text without private authentication or database details.
     */
    @NotBlank
    @Size(min = 3, max = 500)
    // Maps this field to its matching SQL column (non-null, length 500) in the supplied schema.
    @Column(nullable = false, length = 500)
    private String message;

    /**
     * Alert urgency enum; distinct from its source type and read/resolved state.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 20) in the supplied schema.
    @Column(nullable = false, length = 20)
    private Alert.Severity severity = Alert.Severity.INFO;

    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 20) in the supplied schema.
    @Column(nullable = false, length = 20)
    private Alert.Status status = Alert.Status.UNREAD;

    /**
     * Required recipient Employee used for ownership checks and serialized alert reconciliation.
     */
    // @ManyToOne lets many Alert rows reference the same Employee; LAZY defers loading it until needed.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    // An Employee owns the notice; sourceKey correlates generated Room/Task/Inventory conditions.
    // Stores this relationship's foreign key in employee_id, which must be present.
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /**
     * Optional Task context; Room and Inventory alerts can have no Task reference.
     */
    // @ManyToOne lets many Alert rows reference the same Task; LAZY defers loading it until needed.
    @ManyToOne(fetch = FetchType.LAZY)
    // Stores this relationship's foreign key in task_id, which may be null.
    @JoinColumn(name = "task_id")
    private Task task;

    /**
     * Stable source-condition identifier used to suppress equivalent unresolved alerts.
     */
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
    public Alert(Alert.Type type, String message, Employee employee, Task task,
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
        status = Alert.Status.READ;
        if (readAt == null) readAt = now;
    }

    /**
     * Sets RESOLVED and fills missing read and resolution times while preserving earlier timestamps.
     */
    public void resolve(LocalDateTime now) {
        status = Alert.Status.RESOLVED;
        if (readAt == null) readAt = now;
        if (resolvedAt == null) resolvedAt = now;
    }

    public Long getId() { return id; }
    public Alert.Type getType() { return type; }
    public String getMessage() { return message; }
    public Alert.Severity getSeverity() { return severity; }
    public Alert.Status getStatus() { return status; }
    public Employee getEmployee() { return employee; }
    public Task getTask() { return task; }
    public String getSourceKey() { return sourceKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReadAt() { return readAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }


    /**
     * Alert.Status is an enum: a Java type limited to a fixed set of valid choices.
     * Tracks alert lifecycle: UNREAD has not been acknowledged, READ remains unresolved, and RESOLVED preserves finished history.
     */
    public enum Status {
        /**
         * Unacknowledged and unresolved alert.
         */
        UNREAD,
        /**
         * Acknowledged alert still awaiting resolution.
         */
        READ,
        /**
         * Finished alert retained for history.
         */
        RESOLVED }

    /**
     * Alert.Severity is an enum: a Java type limited to a fixed set of valid choices.
     * Describes alert urgency: INFO, WARNING, HIGH, or CRITICAL.
     * Current automated alerts use the Alert entity's INFO default.
     */
    public enum Severity {
        /**
         * Informational urgency and the current default.
         */
        INFO,
        /**
         * Warning urgency represented in the model.
         */
        WARNING,
        /**
         * High urgency represented in the model.
         */
        HIGH,
        /**
         * Critical urgency represented in the model.
         */
        CRITICAL }

    /**
     * Alert.Type is an enum: a Java type limited to a fixed set of valid choices.
     * Identifies the source category used for filtering and automation: ROOM, TASK, INVENTORY, or SYSTEM.
     */
    public enum Type {
        /**
         * Room-readiness source condition.
         */
        ROOM,
        /**
         * Assigned Task overdue or priority source condition.
         */
        TASK,
        /**
         * Stock at or below its threshold.
         */
        INVENTORY,
        /**
         * General system alert category retained in the model.
         */
        SYSTEM }
}

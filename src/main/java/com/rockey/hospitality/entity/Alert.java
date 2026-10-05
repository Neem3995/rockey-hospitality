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

@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alerts_employee_status_created", columnList = "employee_id,status,created_at"),
        @Index(name = "idx_alerts_employee_type_status_source", columnList = "employee_id,type,status,source_key"),
        @Index(name = "idx_alerts_task", columnList = "task_id")
})
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Positive
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertType type = AlertType.SYSTEM;

    @NotBlank
    @Size(min = 3, max = 500)
    @Column(nullable = false, length = 500)
    private String message;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity = AlertSeverity.INFO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status = AlertStatus.UNREAD;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @Size(max = 120)
    @Column(name = "source_key", length = 120)
    private String sourceKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    protected Alert() {
    }

    public Alert(AlertType type, String message, Employee employee, Task task,
                 String sourceKey, LocalDateTime createdAt) {
        this.type = type;
        this.message = message.trim();
        this.employee = employee;
        this.task = task;
        this.sourceKey = sourceKey;
        this.createdAt = createdAt;
    }

    public void markRead(LocalDateTime now) {
        status = AlertStatus.READ;
        if (readAt == null) readAt = now;
    }

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

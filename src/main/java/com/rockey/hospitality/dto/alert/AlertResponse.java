package com.rockey.hospitality.dto.alert;

import com.rockey.hospitality.entity.AlertSeverity;
import com.rockey.hospitality.entity.AlertStatus;
import com.rockey.hospitality.entity.AlertType;
import java.time.LocalDateTime;

public class AlertResponse {

    private final Long id;
    private final AlertType type;
    private final String message;
    private final AlertSeverity severity;
    private final AlertStatus status;
    private final AlertEmployeeSummary employee;
    private final AlertTaskSummary task;
    private final String sourceKey;
    private final LocalDateTime createdAt;
    private final LocalDateTime readAt;
    private final LocalDateTime resolvedAt;

    public AlertResponse(
            Long id,
            AlertType type,
            String message,
            AlertSeverity severity,
            AlertStatus status,
            AlertEmployeeSummary employee,
            AlertTaskSummary task,
            String sourceKey,
            LocalDateTime createdAt,
            LocalDateTime readAt,
            LocalDateTime resolvedAt
    ) {
        this.id = id;
        this.type = type;
        this.message = message;
        this.severity = severity;
        this.status = status;
        this.employee = employee;
        this.task = task;
        this.sourceKey = sourceKey;
        this.createdAt = createdAt;
        this.readAt = readAt;
        this.resolvedAt = resolvedAt;
    }

    public Long getId() {
        return id;
    }

    public AlertType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public AlertEmployeeSummary getEmployee() {
        return employee;
    }

    public AlertTaskSummary getTask() {
        return task;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }
}

package com.rockey.hospitality.dto.alert;

import com.rockey.hospitality.entity.AlertSeverity;
import com.rockey.hospitality.entity.AlertStatus;
import com.rockey.hospitality.entity.AlertType;
import java.time.LocalDateTime;

/**
 * Safe alert response DTO built from validated service results instead of serializing the entity.
 */
public class AlertResponse {

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    private final Long id;
    /**
     * Alert source category used by filtering and automated reconciliation.
     */
    private final AlertType type;
    /**
     * Safe client-facing explanatory text without private authentication or database details.
     */
    private final String message;
    /**
     * Alert urgency enum; distinct from its source type and read/resolved state.
     */
    private final AlertSeverity severity;
    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    private final AlertStatus status;
    /**
     * Shallow recipient or Employee identity rather than a full account graph.
     */
    private final AlertEmployeeSummary employee;
    /**
     * Optional Task summary or association providing alert/work context.
     */
    private final AlertTaskSummary task;
    /**
     * Stable source-condition identifier used to suppress equivalent unresolved alerts.
     */
    private final String sourceKey;
    /**
     * Server-local creation timestamp retained for history.
     */
    private final LocalDateTime createdAt;
    /**
     * First acknowledgement time; null means it has not been recorded.
     */
    private final LocalDateTime readAt;
    /**
     * First resolution time; history is preserved rather than deleted.
     */
    private final LocalDateTime resolvedAt;

    /**
     * Packages the listed response fields supplied by the service without serializing a persistence entity.
     */
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

package com.rockey.hospitality.dto;

import com.rockey.hospitality.entity.Alert;
import java.time.LocalDateTime;

/**
 * STUDY NOTE: DTO means Data Transfer Object: a plain Java type that carries request, response or internal
 * input data.
 * This container groups shallow Alert, Employee and Task responses plus internal search inputs.
 * Controllers and services use these types to separate the API contract from JPA entities and avoid
 * exposing database objects directly.
 * Static nested types need no container instance; validation/JSON annotations describe data, not access
 * permissions.
 */
public final class AlertDtos {

    // A record is a compact Java type with fixed named values; these criteria carry internal filters/paging,
    // not new public API operations.

    // Namespace only; callers construct the nested types instead.
    private AlertDtos() { }

    /**
     * Shallow alert employee response DTO exposing only the listed fields, not a complete JPA relationship graph.
     */
    public static class AlertEmployeeSummary {

        /**
         * Database identifier used to refer to this resource in requests and relationships.
         */
        private final Long id;
        /**
         * Display name used by this resource's request or response, not an authorization role.
         */
        private final String name;

        /**
         * Packages the listed response fields supplied by the service without serializing a persistence entity.
         */
        public AlertEmployeeSummary(
                Long id,
                String name
        ) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * Safe alert response DTO built from validated service results instead of serializing the entity.
     */
    public static class AlertResponse {

        /**
         * Database identifier used to refer to this resource in requests and relationships.
         */
        private final Long id;
        /**
         * Alert source category used by filtering and automated reconciliation.
         */
        private final Alert.Type type;
        /**
         * Safe client-facing explanatory text without private authentication or database details.
         */
        private final String message;
        /**
         * Alert urgency enum; distinct from its source type and read/resolved state.
         */
        private final Alert.Severity severity;
        /**
         * Lifecycle enum value interpreted by this resource's service and transition rules.
         */
        private final Alert.Status status;
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
                Alert.Type type,
                String message,
                Alert.Severity severity,
                Alert.Status status,
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

        public Alert.Type getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public Alert.Severity getSeverity() {
            return severity;
        }

        public Alert.Status getStatus() {
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

/**
     * Immutable optional alert filters passed from controller to service; a null value means that filter was not supplied.
     */
    public static record AlertSearchCriteria(
            /**
             * Optional recipient filter; AlertService restricts STAFF to its own Employee ID.
             */
            Long employeeId,
            /**
             * Optional exact source-type filter.
             */
            Alert.Type type,
            /**
             * Optional lifecycle filter; omission selects unresolved alerts, while explicit RESOLVED selects history.
             */
            Alert.Status status) { }

    /**
     * Shallow alert task response DTO exposing only the listed fields, not a complete JPA relationship graph.
     */
    public static class AlertTaskSummary {

        /**
         * Database identifier used to refer to this resource in requests and relationships.
         */
        private final Long id;
        /**
         * Human-readable work or Event title.
         */
        private final String title;

        /**
         * Packages the listed response fields supplied by the service without serializing a persistence entity.
         */
        public AlertTaskSummary(
                Long id,
                String title
        ) {
            this.id = id;
            this.title = title;
        }

        public Long getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }
    }
}

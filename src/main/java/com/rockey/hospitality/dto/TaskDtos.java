package com.rockey.hospitality.dto;

import com.rockey.hospitality.dto.AuthDtos.DepartmentSummary;
import com.rockey.hospitality.entity.Event;
import com.rockey.hospitality.entity.Task;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * STUDY NOTE: DTO means Data Transfer Object: a plain Java type that carries request, response or internal
 * input data.
 * This container groups Task requests, shallow context summaries, responses and internal search inputs.
 * Controllers and services use these types to separate the API contract from JPA entities and avoid
 * exposing database objects directly.
 * Static nested types need no container instance; validation/JSON annotations describe data, not access
 * permissions.
 */
public final class TaskDtos {

    // A record is a compact Java type with fixed named values; these criteria carry internal filters/paging,
    // not new public API operations.
    // Validation study key (the numbers/patterns are specified on each annotated field):
    // @NotBlank requires non-null text containing at least one non-whitespace character.
    // @NotNull requires a value; it does not check text length or a numeric range.
    // @Size checks length/count against the declared min/max (text length for the fields here).
    // @Positive checks that a supplied number is greater than zero.
    // @FutureOrPresent allows a supplied date/time at the validator's current time or later.
    // Most shape/range validators accept null; @NotNull or @NotBlank supplies required-value checks.

    // Namespace only; callers construct the nested types instead.
    private TaskDtos() { }

    /**
     * Writable assign task JSON DTO, separate from the entity and returned fields.
     * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
     */
    public static class AssignTaskRequest {

        /**
         * Optional new assignee ID; null requests unassignment rather than a placeholder Employee.
         */
        @Positive(message = "Employee must be positive.")
        private Long employeeId;

        public Long getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(Long employeeId) {
            this.employeeId = employeeId;
        }
    }

    /**
     * Writable create task JSON DTO, separate from the entity and returned fields.
     * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
     */
    public static class CreateTaskRequest {

        /**
         * Human-readable work or Event title.
         */
        @NotBlank(message = "Title is required.")
        @Size(min = 3, max = 120, message = "Title must be between 3 and 120 characters.")
        private String title;

        /**
         * Optional descriptive text; services normalize blank values where required.
         */
        @Size(max = 1000, message = "Description must not exceed 1000 characters.")
        private String description;

        /**
         * Department identifier used for an explicit relationship or optional query scope.
         */
        @NotNull(message = "Department is required.")
        @Positive(message = "Department must be positive.")
        private Long departmentId;

        /**
         * Optional assignee filter or reference; service checks enforce eligibility and ownership.
         */
        @Positive(message = "Assigned employee must be positive.")
        private Long assignedEmployeeId;

        /**
         * Optional Room context identifier or search filter.
         */
        @Positive(message = "Room must be positive.")
        private Long roomId;

        /**
         * Optional Event preparation identifier or search filter.
         */
        @Positive(message = "Event must be positive.")
        private Long eventId;

        /**
         * Task urgency enum; the service/entity defaults omitted creation priority to MEDIUM, while updates require a value.
         */
        private Task.Priority priority;

        /**
         * Optional server-local due time; only non-terminal work strictly before now is overdue.
         */
        // Allows a supplied timestamp at the current time or in the future.
        @FutureOrPresent(message = "Due time must be current or future.")
        private LocalDateTime dueAt;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Long getDepartmentId() {
            return departmentId;
        }

        public void setDepartmentId(Long departmentId) {
            this.departmentId = departmentId;
        }

        public Long getAssignedEmployeeId() {
            return assignedEmployeeId;
        }

        public void setAssignedEmployeeId(Long assignedEmployeeId) {
            this.assignedEmployeeId = assignedEmployeeId;
        }

        public Long getRoomId() {
            return roomId;
        }

        public void setRoomId(Long roomId) {
            this.roomId = roomId;
        }

        public Long getEventId() {
            return eventId;
        }

        public void setEventId(Long eventId) {
            this.eventId = eventId;
        }

        public Task.Priority getPriority() {
            return priority;
        }

        public void setPriority(Task.Priority priority) {
            this.priority = priority;
        }

        public LocalDateTime getDueAt() {
            return dueAt;
        }

        public void setDueAt(LocalDateTime dueAt) {
            this.dueAt = dueAt;
        }
    }

    /**
     * Shallow task employee response DTO exposing only the listed fields, not a complete JPA relationship graph.
     */
    public static class TaskEmployeeSummary {

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
        public TaskEmployeeSummary(Long id, String name) {
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
     * Shallow task event response DTO exposing only the listed fields, not a complete JPA relationship graph.
     */
    public static class TaskEventSummary {

        /**
         * Database identifier used to refer to this resource in requests and relationships.
         */
        private final Long id;
        /**
         * Human-readable work or Event title.
         */
        private final String title;
        /**
         * Server-local Event schedule used by registration and lifecycle eligibility.
         */
        private final LocalDateTime eventDateTime;
        /**
         * Lifecycle enum value interpreted by this resource's service and transition rules.
         */
        private final Event.Status status;

        /**
         * Packages the listed response fields supplied by the service without serializing a persistence entity.
         */
        public TaskEventSummary(
                Long id,
                String title,
                LocalDateTime eventDateTime,
                Event.Status status
        ) {
            this.id = id;
            this.title = title;
            this.eventDateTime = eventDateTime;
            this.status = status;
        }

        public Long getId() { return id; }
        public String getTitle() { return title; }
        public LocalDateTime getEventDateTime() { return eventDateTime; }
        public Event.Status getStatus() { return status; }
    }

    /**
     * Safe task response DTO built from validated service results instead of serializing the entity.
     */
    public static class TaskResponse {

        /**
         * Database identifier used to refer to this resource in requests and relationships.
         */
        private final Long id;
        /**
         * Human-readable work or Event title.
         */
        private final String title;
        /**
         * Optional descriptive text; services normalize blank values where required.
         */
        private final String description;
        /**
         * Lifecycle enum value interpreted by this resource's service and transition rules.
         */
        private final Task.Status status;
        /**
         * Task urgency enum; the service/entity defaults omitted creation priority to MEDIUM, while updates require a value.
         */
        private final Task.Priority priority;
        /**
         * Shallow related Department in DTOs, or the owning Department association in entities.
         */
        private final DepartmentSummary department;
        /**
         * Optional shallow Task assignee summary.
         */
        private final TaskEmployeeSummary assignedEmployee;
        /**
         * Optional shallow Room context without a persistence graph.
         */
        private final TaskRoomSummary room;
        /**
         * Optional shallow Event context or registered Event summary.
         */
        private final TaskEventSummary event;
        /**
         * Server-local creation timestamp retained for history.
         */
        private final LocalDateTime createdAt;
        /**
         * Optional server-local due time; only non-terminal work strictly before now is overdue.
         */
        private final LocalDateTime dueAt;
        /**
         * Completion timestamp for COMPLETED work, distinct from cancellation.
         */
        private final LocalDateTime completedAt;
        /**
         * Server-local timestamp of the latest persisted entity update.
         */
        private final LocalDateTime updatedAt;

        /**
         * Packages the listed response fields supplied by the service without serializing a persistence entity.
         */
        public TaskResponse(
                Long id,
                String title,
                String description,
                Task.Status status,
                Task.Priority priority,
                DepartmentSummary department,
                TaskEmployeeSummary assignedEmployee,
                TaskRoomSummary room,
                TaskEventSummary event,
                LocalDateTime createdAt,
                LocalDateTime dueAt,
                LocalDateTime completedAt,
                LocalDateTime updatedAt
        ) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.status = status;
            this.priority = priority;
            this.department = department;
            this.assignedEmployee = assignedEmployee;
            this.room = room;
            this.event = event;
            this.createdAt = createdAt;
            this.dueAt = dueAt;
            this.completedAt = completedAt;
            this.updatedAt = updatedAt;
        }

        /**
         * Delegates to the full response constructor with omitted optional context set to null.
         */
        public TaskResponse(
                Long id,
                String title,
                String description,
                Task.Status status,
                Task.Priority priority,
                DepartmentSummary department,
                TaskEmployeeSummary assignedEmployee,
                TaskRoomSummary room,
                LocalDateTime createdAt,
                LocalDateTime dueAt,
                LocalDateTime completedAt,
                LocalDateTime updatedAt
        ) {
            this(
                    id,
                    title,
                    description,
                    status,
                    priority,
                    department,
                    assignedEmployee,
                    room,
                    null,
                    createdAt,
                    dueAt,
                    completedAt,
                    updatedAt
            );
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

        public DepartmentSummary getDepartment() {
            return department;
        }

        public TaskEmployeeSummary getAssignedEmployee() {
            return assignedEmployee;
        }

        public TaskRoomSummary getRoom() {
            return room;
        }

        public TaskEventSummary getEvent() {
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

    /**
     * Shallow task room response DTO exposing only the listed fields, not a complete JPA relationship graph.
     */
    public static class TaskRoomSummary {

        /**
         * Database identifier used to refer to this resource in requests and relationships.
         */
        private final Long id;
        /**
         * Unique operational Room identifier; it is not a database ID or a booking.
         */
        private final String roomNumber;

        /**
         * Packages the listed response fields supplied by the service without serializing a persistence entity.
         */
        public TaskRoomSummary(Long id, String roomNumber) {
            this.id = id;
            this.roomNumber = roomNumber;
        }

        public Long getId() {
            return id;
        }

        public String getRoomNumber() {
            return roomNumber;
        }
    }

/**
     * Immutable optional Task filters used by the service and parameterized repository search.
     * overdue=true, false, and null retain their distinct contract meanings.
     */
    public static record TaskSearchCriteria(
            /**
             * Optional Department filter, distinct from a writable Task relationship.
             */
            Long departmentId,
            /**
             * Optional exact-status filter, including terminal history when selected.
             */
            Task.Status status,
            /**
             * Optional exact-priority filter; null leaves priority unfiltered.
             */
            Task.Priority priority,
            /**
             * Optional assignee filter or reference; service checks enforce eligibility and ownership.
             */
            Long assignedEmployeeId,
            /**
             * Optional Room context identifier or search filter.
             */
            Long roomId,
            /**
             * Optional Event preparation identifier or search filter.
             */
            Long eventId,
            /**
             * Optional due-condition filter: true selects overdue work, false selects its complement, and null leaves due time unfiltered.
             */
            Boolean overdue) { }

    /**
     * Writable update task JSON DTO, separate from the entity and returned fields.
     * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
     */
    public static class UpdateTaskRequest {

        /**
         * Human-readable work or Event title.
         */
        @NotBlank(message = "Title is required.")
        @Size(min = 3, max = 120, message = "Title must be between 3 and 120 characters.")
        private String title;

        /**
         * Optional descriptive text; services normalize blank values where required.
         */
        @Size(max = 1000, message = "Description must not exceed 1000 characters.")
        private String description;

        /**
         * Department identifier used for an explicit relationship or optional query scope.
         */
        @NotNull(message = "Department is required.")
        @Positive(message = "Department must be positive.")
        private Long departmentId;

        /**
         * Optional assignee filter or reference; service checks enforce eligibility and ownership.
         */
        @Positive(message = "Assigned employee must be positive.")
        private Long assignedEmployeeId;

        /**
         * Optional Room context identifier or search filter.
         */
        @Positive(message = "Room must be positive.")
        private Long roomId;

        /**
         * Optional Event preparation identifier or search filter.
         */
        @Positive(message = "Event must be positive.")
        private Long eventId;

        /**
         * Task urgency enum; the service/entity defaults omitted creation priority to MEDIUM, while updates require a value.
         */
        @NotNull(message = "Priority is required.")
        private Task.Priority priority;

        /**
         * Lifecycle enum value interpreted by this resource's service and transition rules.
         */
        @NotNull(message = "Status is required.")
        private Task.Status status;

        /**
         * Optional server-local due time; only non-terminal work strictly before now is overdue.
         */
        private LocalDateTime dueAt;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Long getDepartmentId() {
            return departmentId;
        }

        public void setDepartmentId(Long departmentId) {
            this.departmentId = departmentId;
        }

        public Long getAssignedEmployeeId() {
            return assignedEmployeeId;
        }

        public void setAssignedEmployeeId(Long assignedEmployeeId) {
            this.assignedEmployeeId = assignedEmployeeId;
        }

        public Long getRoomId() {
            return roomId;
        }

        public void setRoomId(Long roomId) {
            this.roomId = roomId;
        }

        public Long getEventId() {
            return eventId;
        }

        public void setEventId(Long eventId) {
            this.eventId = eventId;
        }

        public Task.Priority getPriority() {
            return priority;
        }

        public void setPriority(Task.Priority priority) {
            this.priority = priority;
        }

        public Task.Status getStatus() {
            return status;
        }

        public void setStatus(Task.Status status) {
            this.status = status;
        }

        public LocalDateTime getDueAt() {
            return dueAt;
        }

        public void setDueAt(LocalDateTime dueAt) {
            this.dueAt = dueAt;
        }
    }
}

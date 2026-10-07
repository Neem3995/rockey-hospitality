package com.rockey.hospitality.dto.task;

import com.rockey.hospitality.dto.auth.DepartmentSummary;
import com.rockey.hospitality.entity.TaskPriority;
import com.rockey.hospitality.entity.TaskStatus;

import java.time.LocalDateTime;

/**
 * Safe task response DTO built from validated service results instead of serializing the entity.
 */
public class TaskResponse {

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
    private final TaskStatus status;
    /**
     * Task urgency enum; the service/entity defaults omitted creation priority to MEDIUM, while updates require a value.
     */
    private final TaskPriority priority;
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
            TaskStatus status,
            TaskPriority priority,
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
            TaskStatus status,
            TaskPriority priority,
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

    public TaskStatus getStatus() {
        return status;
    }

    public TaskPriority getPriority() {
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

package com.rockey.hospitality.dto.task;

import com.rockey.hospitality.entity.TaskPriority;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Writable create task JSON DTO, separate from the entity and returned fields.
 * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
 */
public class CreateTaskRequest {

    /**
     * Human-readable work or Event title.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank(message = "Title is required.")
    // Checks supplied text length from 3 to 120 characters; required text is checked separately.
    @Size(min = 3, max = 120, message = "Title must be between 3 and 120 characters.")
    private String title;

    /**
     * Optional descriptive text; services normalize blank values where required.
     */
    // Checks supplied text length up to 1000 characters; required text is checked separately.
    @Size(max = 1000, message = "Description must not exceed 1000 characters.")
    private String description;

    /**
     * Department identifier used for an explicit relationship or optional query scope.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull(message = "Department is required.")
    // Requires a supplied number to be greater than zero; null is handled separately.
    @Positive(message = "Department must be positive.")
    private Long departmentId;

    /**
     * Optional assignee filter or reference; service checks enforce eligibility and ownership.
     */
    // Requires a supplied number to be greater than zero; null is handled separately.
    @Positive(message = "Assigned employee must be positive.")
    private Long assignedEmployeeId;

    /**
     * Optional Room context identifier or search filter.
     */
    // Requires a supplied number to be greater than zero; null is handled separately.
    @Positive(message = "Room must be positive.")
    private Long roomId;

    /**
     * Optional Event preparation identifier or search filter.
     */
    // Requires a supplied number to be greater than zero; null is handled separately.
    @Positive(message = "Event must be positive.")
    private Long eventId;

    /**
     * Task urgency enum; the service/entity defaults omitted creation priority to MEDIUM, while updates require a value.
     */
    private TaskPriority priority;

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

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(LocalDateTime dueAt) {
        this.dueAt = dueAt;
    }
}

package com.rockey.hospitality.dto.task;

import com.rockey.hospitality.entity.TaskPriority;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class CreateTaskRequest {

    @NotBlank(message = "Title is required.")
    @Size(min = 3, max = 120, message = "Title must be between 3 and 120 characters.")
    private String title;

    @Size(max = 1000, message = "Description must not exceed 1000 characters.")
    private String description;

    @NotNull(message = "Department is required.")
    @Positive(message = "Department must be positive.")
    private Long departmentId;

    @Positive(message = "Assigned employee must be positive.")
    private Long assignedEmployeeId;

    @Positive(message = "Room must be positive.")
    private Long roomId;

    @Positive(message = "Event must be positive.")
    private Long eventId;

    private TaskPriority priority;

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

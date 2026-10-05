package com.rockey.hospitality.dto.task;

import com.rockey.hospitality.dto.auth.DepartmentSummary;
import com.rockey.hospitality.entity.TaskPriority;
import com.rockey.hospitality.entity.TaskStatus;

import java.time.LocalDateTime;

public class TaskResponse {

    private final Long id;
    private final String title;
    private final String description;
    private final TaskStatus status;
    private final TaskPriority priority;
    private final DepartmentSummary department;
    private final TaskEmployeeSummary assignedEmployee;
    private final TaskRoomSummary room;
    private final TaskEventSummary event;
    private final LocalDateTime createdAt;
    private final LocalDateTime dueAt;
    private final LocalDateTime completedAt;
    private final LocalDateTime updatedAt;

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

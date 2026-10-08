package com.rockey.hospitality.dto;

import com.rockey.hospitality.entity.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/**
 * STUDY NOTE: DTOs (data transfer objects) are the JSON input/output shapes of the API, not database entities.
 * Bean Validation annotations such as @NotBlank, @Size, @NotNull and @Positive check task input.
 * TaskStatusRequest carries only the requested next status; TaskService decides whether that move is allowed.
 * TaskResponse nests a safe assignee summary and the room's current state for the task list.
 */
public final class TaskDtos {
    private TaskDtos() { }
    /** Validated JSON input; no entity or trusted caller identity is accepted from the browser. */
    public static class TaskRequest {
        @NotBlank @Size(min = 2, max = 150)
        private String title;
        @Size(max = 1000)
        private String description;
        @NotNull
        private Task.Priority priority;
        @NotNull @Positive
        private Long assignedUserId;
        @NotNull @Positive
        private Long roomId;
        private LocalDateTime dueAt;
        /** Jackson constructs the request, then populates properties. */
        public TaskRequest() { }
        public TaskRequest(String title, String description, Task.Priority priority, Long assignedUserId, Long roomId, LocalDateTime dueAt) {
            this.title = title;
            this.description = description;
            this.priority = priority;
            this.assignedUserId = assignedUserId;
            this.roomId = roomId;
            this.dueAt = dueAt;
        }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Task.Priority getPriority() { return priority; }
        public void setPriority(Task.Priority priority) { this.priority = priority; }
        public Long getAssignedUserId() { return assignedUserId; }
        public void setAssignedUserId(Long assignedUserId) { this.assignedUserId = assignedUserId; }
        public Long getRoomId() { return roomId; }
        public void setRoomId(Long roomId) { this.roomId = roomId; }
        public LocalDateTime getDueAt() { return dueAt; }
        public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }
    }

    /** Validated JSON input; no entity or trusted caller identity is accepted from the browser. */
    public static class TaskStatusRequest {
        @NotNull
        private Task.Status status;
        /** Jackson constructs the request, then populates properties. */
        public TaskStatusRequest() { }
        public TaskStatusRequest(Task.Status status) {
            this.status = status;
        }
        public Task.Status getStatus() { return status; }
        public void setStatus(Task.Status status) { this.status = status; }
    }

    /** Safe JSON output, without password hashes or refresh session data. */
    public static class TaskResponse {
        private final Long id;
        private final String title;
        private final String description;
        private final Task.Status status;
        private final Task.Priority priority;
        private final UserDtos.UserSummary assignedUser;
        private final RoomDtos.RoomResponse room;
        private final LocalDateTime dueAt;
        private final LocalDateTime completedAt;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
        public TaskResponse(Long id, String title, String description, Task.Status status, Task.Priority priority, UserDtos.UserSummary assignedUser, RoomDtos.RoomResponse room, LocalDateTime dueAt, LocalDateTime completedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.status = status;
            this.priority = priority;
            this.assignedUser = assignedUser;
            this.room = room;
            this.dueAt = dueAt;
            this.completedAt = completedAt;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
        public Long getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public Task.Status getStatus() { return status; }
        public Task.Priority getPriority() { return priority; }
        public UserDtos.UserSummary getAssignedUser() { return assignedUser; }
        public RoomDtos.RoomResponse getRoom() { return room; }
        public LocalDateTime getDueAt() { return dueAt; }
        public LocalDateTime getCompletedAt() { return completedAt; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
    }
}

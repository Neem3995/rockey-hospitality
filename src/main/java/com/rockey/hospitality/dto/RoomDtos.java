package com.rockey.hospitality.dto;

import com.rockey.hospitality.entity.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/**
 * STUDY NOTE: These classes carry room and inspection data between JSON and the controller/service.
 * Spring validates request fields before RoomService sees them; services build the response objects.
 * InspectionRequest supplies taskId, result and optional notes, but not an inspecting user id.
 * RoomService obtains that identity from the authenticated caller and returns a safe summary.
 * Validation catches bad field shapes; it does not prove a room can change state or be inspected.
 */
public final class RoomDtos {
    private RoomDtos() { }
    /** Validated JSON input; no entity or trusted caller identity is accepted from the browser. */
    public static class RoomRequest {
        @NotBlank @Size(max = 10)
        private String roomNumber;
        @NotNull @Min(1) @Max(99)
        private Integer floor;
        private Room.Status status;
        private Boolean active;
        /** Jackson constructs the request, then populates properties. */
        public RoomRequest() { }
        public RoomRequest(String roomNumber, Integer floor, Room.Status status, Boolean active) {
            this.roomNumber = roomNumber;
            this.floor = floor;
            this.status = status;
            this.active = active;
        }
        public String getRoomNumber() { return roomNumber; }
        public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
        public Integer getFloor() { return floor; }
        public void setFloor(Integer floor) { this.floor = floor; }
        public Room.Status getStatus() { return status; }
        public void setStatus(Room.Status status) { this.status = status; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }

    /** Validated JSON input; no entity or trusted caller identity is accepted from the browser. */
    public static class RoomStatusRequest {
        @NotNull
        private Room.Status status;
        /** Jackson constructs the request, then populates properties. */
        public RoomStatusRequest() { }
        public RoomStatusRequest(Room.Status status) {
            this.status = status;
        }
        public Room.Status getStatus() { return status; }
        public void setStatus(Room.Status status) { this.status = status; }
    }

    /** Safe JSON output, without password hashes or refresh session data. */
    public static class RoomResponse {
        private final Long id;
        private final String roomNumber;
        private final Integer floor;
        private final Room.Status status;
        private final boolean active;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
        public RoomResponse(Long id, String roomNumber, Integer floor, Room.Status status, boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {
            this.id = id;
            this.roomNumber = roomNumber;
            this.floor = floor;
            this.status = status;
            this.active = active;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
        public Long getId() { return id; }
        public String getRoomNumber() { return roomNumber; }
        public Integer getFloor() { return floor; }
        public Room.Status getStatus() { return status; }
        public boolean getActive() { return active; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
    }

    /** Validated JSON input; no entity or trusted caller identity is accepted from the browser. */
    public static class InspectionRequest {
        @NotNull @Positive
        private Long taskId;
        @NotNull
        private Inspection.Result result;
        @Size(max = 1000)
        private String notes;
        /** Jackson constructs the request, then populates properties. */
        public InspectionRequest() { }
        public InspectionRequest(Long taskId, Inspection.Result result, String notes) {
            this.taskId = taskId;
            this.result = result;
            this.notes = notes;
        }
        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        public Inspection.Result getResult() { return result; }
        public void setResult(Inspection.Result result) { this.result = result; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    /** Safe JSON output, without password hashes or refresh session data. */
    public static class InspectionResponse {
        private final Long id;
        private final Long roomId;
        private final Long taskId;
        private final UserDtos.UserSummary inspectedBy;
        private final Inspection.Result result;
        private final String notes;
        private final LocalDateTime inspectedAt;
        public InspectionResponse(Long id, Long roomId, Long taskId, UserDtos.UserSummary inspectedBy, Inspection.Result result, String notes, LocalDateTime inspectedAt) {
            this.id = id;
            this.roomId = roomId;
            this.taskId = taskId;
            this.inspectedBy = inspectedBy;
            this.result = result;
            this.notes = notes;
            this.inspectedAt = inspectedAt;
        }
        public Long getId() { return id; }
        public Long getRoomId() { return roomId; }
        public Long getTaskId() { return taskId; }
        public UserDtos.UserSummary getInspectedBy() { return inspectedBy; }
        public Inspection.Result getResult() { return result; }
        public String getNotes() { return notes; }
        public LocalDateTime getInspectedAt() { return inspectedAt; }
    }
}

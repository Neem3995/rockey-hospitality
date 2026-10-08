package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.RoomDtos.*;
import com.rockey.hospitality.entity.*;
import com.rockey.hospitality.exception.ApiException.*;
import com.rockey.hospitality.repository.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STUDY NOTE: @Service holds room/readiness rules, including the small inspection use case.
 * @Transactional makes inspection history and PASS/FAIL room changes succeed or roll back together.
 * Controllers pass authenticated identity; locked room rows serialize competing work and inspection changes.
 * Inspection time is hotel-local LocalDateTime; the UTC Clock is viewed in the configured JVM hotel zone.
 * DTOs expose safe fields while repositories reach the four-table MySQL schema.
 */
@Service
public class RoomService {
    private final RoomRepository rooms;
    private final TaskRepository tasks;
    private final InspectionRepository inspections;
    private final UserService users;
    private final Clock clock;
    public RoomService(RoomRepository rooms, TaskRepository tasks, InspectionRepository inspections,
                       UserService users, Clock clock) {
        this.rooms = rooms; this.tasks = tasks; this.inspections = inspections; this.users = users; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> list(Long actorId) {
        users.requireSupervisor(actorId);
        return rooms.findAllByOrderByRoomNumberAsc().stream().map(RoomService::response).toList();
    }
    @Transactional(readOnly = true)
    public RoomResponse get(Long actorId, Long id) { users.requireSupervisor(actorId); return response(room(id)); }

    @Transactional
    public RoomResponse create(Long actorId, RoomRequest request) {
        users.requireSupervisor(actorId);
        String number = request.getRoomNumber().trim();
        if (rooms.existsByRoomNumberIgnoreCase(number)) throw new ConflictException("Room number already exists.");
        Room.Status status = request.getStatus() == null ? Room.Status.READY : request.getStatus();
        if (status == Room.Status.CLEANING || status == Room.Status.INSPECTION) {
            throw new ConflictException("Cleaning and inspection states require completed workflow steps.");
        }
        Room room = new Room(number, request.getFloor(), status);
        if (Boolean.FALSE.equals(request.getActive())) room.deactivate();
        return response(rooms.save(room));
    }

    @Transactional
    public RoomResponse update(Long actorId, Long id, RoomRequest request) {
        users.requireSupervisor(actorId);
        Room room = lockedRoom(id);
        if (!room.getRoomNumber().equals(request.getRoomNumber().trim())) throw new ConflictException("Room number cannot change.");
        if (request.getStatus() != null && request.getStatus() != room.getStatus()) {
            throw new ConflictException("Use the status operation for lifecycle changes.");
        }
        boolean active = request.getActive() == null ? room.isActive() : request.getActive();
        if (!active) guardWork(room);
        room.update(request.getFloor(), active);
        return response(rooms.save(room));
    }

    @Transactional
    public RoomResponse status(Long actorId, Long id, Room.Status next) {
        users.requireSupervisor(actorId);
        Room room = lockedRoom(id);
        requireActive(room);
        if (room.getStatus() == next) return response(room);
        guardWork(room);
        boolean allowed = room.getStatus() == Room.Status.READY
                && (next == Room.Status.DIRTY || next == Room.Status.OUT_OF_SERVICE)
                || room.getStatus() == Room.Status.DIRTY && next == Room.Status.OUT_OF_SERVICE
                || room.getStatus() == Room.Status.OUT_OF_SERVICE && next == Room.Status.DIRTY;
        if (!allowed) throw new ConflictException("Room transition is not allowed; readiness requires a passing inspection.");
        room.updateStatus(next);
        return response(rooms.save(room));
    }

    @Transactional
    public void deactivate(Long actorId, Long id) {
        users.requireSupervisor(actorId);
        Room room = lockedRoom(id);
        guardWork(room);
        room.deactivate();
        rooms.save(room);
    }

    @Transactional(readOnly = true)
    public List<InspectionResponse> inspectionHistory(Long actorId, Long roomId) {
        users.requireSupervisor(actorId);
        room(roomId);
        return inspections.findByRoomIdOrderByIdDesc(roomId).stream().map(this::inspectionResponse).toList();
    }

    @Transactional
    public InspectionResponse inspect(Long actorId, Long roomId, InspectionRequest request) {
        User supervisor = users.requireSupervisor(actorId);
        Room room = lockedRoom(roomId);
        requireActive(room);
        Task task = tasks.findById(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task not found."));
        if (!task.getRoom().getId().equals(roomId) || task.getStatus() != Task.Status.COMPLETED
                || room.getStatus() != Room.Status.INSPECTION) {
            throw new ConflictException("Inspection requires this room's completed cleaning task and pending inspection.");
        }
        Task latest = tasks.findFirstByRoomIdAndStatusOrderByIdDesc(roomId, Task.Status.COMPLETED)
                .orElseThrow(() -> new ConflictException("No completed cleaning task exists."));
        if (!latest.getId().equals(task.getId())) throw new ConflictException("Inspect the latest completed cleaning task.");
        Inspection inspection = new Inspection(room, task, supervisor, request.getResult(), request.getNotes(),
                LocalDateTime.now(clock.withZone(ZoneId.systemDefault())));
        inspections.save(inspection);
        room.updateStatus(request.getResult() == Inspection.Result.PASS ? Room.Status.READY : Room.Status.DIRTY);
        rooms.save(room);
        return inspectionResponse(inspection);
    }

    private Room room(Long id) { return rooms.findById(id).orElseThrow(() -> new ResourceNotFoundException("Room not found.")); }
    private Room lockedRoom(Long id) { return rooms.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Room not found.")); }
    private void requireActive(Room room) { if (!room.isActive()) throw new ConflictException("Room is inactive."); }
    private void guardWork(Room room) {
        if (room.getStatus() == Room.Status.INSPECTION
                || !tasks.findByRoomIdAndStatusIn(room.getId(), List.of(Task.Status.ASSIGNED, Task.Status.IN_PROGRESS)).isEmpty()) {
            throw new ConflictException("Finish or cancel active work and record pending inspections first.");
        }
    }
    public static RoomResponse response(Room room) {
        return new RoomResponse(room.getId(), room.getRoomNumber(), room.getFloor(), room.getStatus(),
                room.isActive(), room.getCreatedAt(), room.getUpdatedAt());
    }
    private InspectionResponse inspectionResponse(Inspection inspection) {
        return new InspectionResponse(inspection.getId(), inspection.getRoom().getId(), inspection.getTask().getId(),
                UserService.summary(inspection.getInspectedBy()), inspection.getResult(), inspection.getNotes(), inspection.getInspectedAt());
    }
}

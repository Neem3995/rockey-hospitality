package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.TaskDtos.*;
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
 * STUDY NOTE: This service decides whether a task action is allowed and coordinates its room change.
 * TaskController calls it with the caller id, task id and/or validated request values.
 * First we check identity and rules, then use the repositories to load or lock the needed entities.
 * When assignment needs a user lock, mutable rows are locked User -> Room -> Task; an id lookup
 * may happen first. Starting/completing work changes both Task and Room in one transaction.
 * We return TaskResponse, not the entities. Ordinary runtime failures roll the transaction back.
 * This layer does not parse HTTP or record inspections; RoomService handles inspections.
 */
@Service
public class TaskService {
    private final TaskRepository tasks;
    private final RoomRepository rooms;
    private final UserRepository accounts;
    private final UserService users;
    private final Clock clock;
    public TaskService(TaskRepository tasks, RoomRepository rooms, UserRepository accounts, UserService users, Clock clock) {
        this.tasks = tasks; this.rooms = rooms; this.accounts = accounts; this.users = users; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(Long actorId) {
        User actor = activeActor(actorId);
        List<Task> rows = actor.getRole() == User.Role.USER
                ? tasks.findByAssignedUserIdOrderByIdDesc(actorId) : tasks.findAllByOrderByIdDesc();
        return rows.stream().map(TaskService::response).toList();
    }
    @Transactional(readOnly = true)
    public TaskResponse get(Long actorId, Long id) {
        User actor = activeActor(actorId);
        Task task = task(id); ownership(actor, task);
        return response(task);
    }

    @Transactional
    public TaskResponse create(Long actorId, TaskRequest request) {
        users.requireSupervisor(actorId);
        User assignee = assignee(request.getAssignedUserId());
        Room room = lockedRoom(request.getRoomId());
        if (!room.isActive() || room.getStatus() != Room.Status.DIRTY) throw new ConflictException("New work requires an active dirty room.");
        if (!tasks.findByRoomIdAndStatusIn(room.getId(), List.of(Task.Status.ASSIGNED, Task.Status.IN_PROGRESS)).isEmpty()) {
            throw new ConflictException("Room already has active cleaning work.");
        }
        Task task = new Task(request.getTitle().trim(), request.getDescription(), request.getPriority(), assignee, room, request.getDueAt());
        return response(tasks.save(task));
    }

    @Transactional
    public TaskResponse update(Long actorId, Long id, TaskRequest request) {
        users.requireSupervisor(actorId);
        User assignee = assignee(request.getAssignedUserId());
        Room room = roomForTask(id);
        Task task = lockedTask(id);
        if (task.isTerminal()) throw new ConflictException("Terminal task history cannot be edited.");
        if (!room.isActive() || !room.getId().equals(request.getRoomId())) throw new ConflictException("Task room is inactive or cannot be changed.");
        task.update(request.getTitle().trim(), request.getDescription(), request.getPriority(), assignee, request.getDueAt());
        return response(tasks.save(task));
    }

    @Transactional
    public TaskResponse status(Long actorId, Long id, Task.Status next) {
        User actor = activeActor(actorId);
        if ((next == Task.Status.IN_PROGRESS || next == Task.Status.COMPLETED) && actor.getRole() != User.Role.USER) {
            throw new ForbiddenException("Only the assigned housekeeper may start or complete work.");
        }
        Room room = roomForTask(id);
        Task task = lockedTask(id);
        ownership(actor, task);
        if (!room.isActive() || !task.getAssignedUser().isActive()
                || task.getAssignedUser().getRole() != User.Role.USER) throw new ConflictException("Room and assigned housekeeper must remain active.");
        if (next == Task.Status.IN_PROGRESS && task.getStatus() == Task.Status.ASSIGNED && room.getStatus() == Room.Status.DIRTY) {
            // All three states must fit: the requested move, stored task and stored room.
            // Now both managed objects change in this transaction; neither save means a separate commit.
            task.start(); room.updateStatus(Room.Status.CLEANING);
        } else if (next == Task.Status.COMPLETED && task.getStatus() == Task.Status.IN_PROGRESS && room.getStatus() == Room.Status.CLEANING) {
            task.complete(LocalDateTime.now(clock.withZone(ZoneId.systemDefault())));
            room.updateStatus(Room.Status.INSPECTION);
        } else if (next == Task.Status.CANCELLED && actor.getRole() != User.Role.USER && !task.isTerminal()) {
            cancel(task, room);
        } else throw new ConflictException("Task transition is not allowed.");
        rooms.save(room);
        return response(tasks.save(task));
    }

    @Transactional
    public void cancel(Long actorId, Long id) {
        users.requireSupervisor(actorId);
        Room room = roomForTask(id);
        Task task = lockedTask(id);
        if (task.getStatus() == Task.Status.CANCELLED) return;
        if (task.getStatus() == Task.Status.COMPLETED) throw new ConflictException("Completed task history cannot be cancelled.");
        cancel(task, room);
        tasks.save(task); rooms.save(room);
    }

    private void cancel(Task task, Room room) {
        if (task.getStatus() == Task.Status.IN_PROGRESS) room.updateStatus(Room.Status.DIRTY);
        task.cancel();
    }
    private User activeActor(Long id) {
        User actor = users.account(id);
        if (!actor.isActive()) throw new ForbiddenException("Account is inactive.");
        return actor;
    }
    private User assignee(Long id) {
        User user = accounts.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Housekeeper not found."));
        if (!user.isActive() || user.getRole() != User.Role.USER) throw new ConflictException("Assignment requires an active USER housekeeper.");
        return user;
    }
    private void ownership(User actor, Task task) {
        if (actor.getRole() == User.Role.USER && !task.getAssignedUser().getId().equals(actor.getId())) {
            throw new ForbiddenException("Only your assigned work is accessible.");
        }
    }
    private Room roomForTask(Long id) {
        // First read only the task's room id, then lock that Room. lockedTask follows in the caller.
        // This preliminary lookup is not a task write lock and does not authorize the request.
        Long roomId = tasks.findRoomId(id).orElseThrow(() -> new ResourceNotFoundException("Task not found."));
        return lockedRoom(roomId);
    }
    private Room lockedRoom(Long id) { return rooms.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Room not found.")); }
    private Task lockedTask(Long id) { return tasks.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Task not found.")); }
    private Task task(Long id) { return tasks.findById(id).orElseThrow(() -> new ResourceNotFoundException("Task not found.")); }
    public static TaskResponse response(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getStatus(), task.getPriority(),
                UserService.summary(task.getAssignedUser()), RoomService.response(task.getRoom()), task.getDueAt(),
                task.getCompletedAt(), task.getCreatedAt(), task.getUpdatedAt());
    }
}

package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.auth.DepartmentSummary;
import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.dto.common.PageCriteria;
import com.rockey.hospitality.dto.task.TaskSearchCriteria;
import com.rockey.hospitality.dto.task.CreateTaskRequest;
import com.rockey.hospitality.dto.task.TaskEventSummary;
import com.rockey.hospitality.dto.task.TaskEmployeeSummary;
import com.rockey.hospitality.dto.task.TaskResponse;
import com.rockey.hospitality.dto.task.TaskRoomSummary;
import com.rockey.hospitality.dto.task.UpdateTaskRequest;
import com.rockey.hospitality.entity.Department;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.EmployeeStatus;
import com.rockey.hospitality.entity.Event;
import com.rockey.hospitality.entity.EventStatus;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.Room;
import com.rockey.hospitality.entity.Task;
import com.rockey.hospitality.entity.TaskPriority;
import com.rockey.hospitality.entity.TaskStatus;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.exception.BadRequestException;
import com.rockey.hospitality.exception.ConflictException;
import com.rockey.hospitality.exception.ForbiddenException;
import com.rockey.hospitality.exception.ResourceNotFoundException;
import com.rockey.hospitality.repository.DepartmentRepository;
import com.rockey.hospitality.repository.EmployeeRepository;
import com.rockey.hospitality.repository.EventRepository;
import com.rockey.hospitality.repository.RoomRepository;
import com.rockey.hospitality.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class TaskService {

    public static final Set<TaskStatus> NON_TERMINAL_STATUSES = Set.of(
            TaskStatus.OPEN,
            TaskStatus.ASSIGNED,
            TaskStatus.IN_PROGRESS
    );
    public static final Set<TaskStatus> TERMINAL_STATUSES = Set.of(
            TaskStatus.COMPLETED,
            TaskStatus.CANCELLED
    );
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "title",
            "status",
            "priority",
            "dueAt",
            "createdAt",
            "updatedAt"
    );
    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = Map.of(
            TaskStatus.OPEN,
            Set.of(TaskStatus.ASSIGNED, TaskStatus.CANCELLED),
            TaskStatus.ASSIGNED,
            Set.of(
                    TaskStatus.OPEN,
                    TaskStatus.IN_PROGRESS,
                    TaskStatus.COMPLETED,
                    TaskStatus.CANCELLED
            ),
            TaskStatus.IN_PROGRESS,
            Set.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED),
            TaskStatus.COMPLETED,
            Set.of(),
            TaskStatus.CANCELLED,
            Set.of()
    );

    private final TaskRepository taskRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final RoomRepository roomRepository;
    private final EventRepository eventRepository;

    public TaskService(
            TaskRepository taskRepository,
            DepartmentRepository departmentRepository,
            EmployeeRepository employeeRepository,
            RoomRepository roomRepository,
            EventRepository eventRepository
    ) {
        this.taskRepository = taskRepository;
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.roomRepository = roomRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> listTasks(TaskSearchCriteria criteria, PageCriteria pagination) {
        validateOptionalPositiveId(criteria.departmentId(), "Department filter");
        validateOptionalPositiveId(criteria.assignedEmployeeId(), "Employee filter");
        validateOptionalPositiveId(criteria.roomId(), "Room filter");
        validateOptionalPositiveId(criteria.eventId(), "Event filter");
        return search(criteria, pagination);
    }

    @Transactional
    public TaskResponse createTask(CreateTaskRequest request) {
        LocalDateTime now = LocalDateTime.now();
        validateNewDueAt(request.getDueAt(), now);
        Department department = findActiveDepartment(request.getDepartmentId());
        Employee employee = findOptionalActiveEmployee(request.getAssignedEmployeeId());
        Room room = findOptionalActiveRoom(request.getRoomId());
        Event event = findOptionalEligibleEvent(request.getEventId());

        Task task = new Task(
                request.getTitle().trim(),
                normalizeDescription(request.getDescription()),
                department,
                employee,
                room,
                event,
                request.getPriority(),
                request.getDueAt()
        );
        return toResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(Long taskId, Long requesterUserId, Role requesterRole) {
        Task task = findTask(taskId);
        ensureTaskAccess(task, requesterUserId, requesterRole);
        return toResponse(task);
    }

    @Transactional
    public TaskResponse updateTask(Long taskId, UpdateTaskRequest request) {
        Task task = findTask(taskId);
        ensureNotTerminal(task);
        Department department = findActiveDepartment(request.getDepartmentId());
        Employee employee = findOptionalActiveEmployee(request.getAssignedEmployeeId());
        Room room = findOptionalActiveRoom(request.getRoomId());
        Event event = findOptionalEligibleEvent(request.getEventId());
        LocalDateTime now = LocalDateTime.now();
        if (!Objects.equals(task.getDueAt(), request.getDueAt())) {
            validateNewDueAt(request.getDueAt(), now);
        }

        TaskStatus requestedStatus = request.getStatus();
        if (task.getStatus() != requestedStatus) {
            ensureTransitionAllowed(task.getStatus(), requestedStatus);
        }
        validateStatusAndAssignee(requestedStatus, employee);
        ensureTerminalTransitionPreservesAssignee(task, requestedStatus, employee);

        LocalDateTime completedAt = requestedStatus == TaskStatus.COMPLETED ? now : null;
        task.update(
                request.getTitle().trim(),
                normalizeDescription(request.getDescription()),
                department,
                employee,
                room,
                event,
                request.getPriority(),
                requestedStatus,
                request.getDueAt(),
                completedAt
        );
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse cancelTask(Long taskId) {
        Task task = findTask(taskId);
        ensureNotTerminal(task);
        ensureTransitionAllowed(task.getStatus(), TaskStatus.CANCELLED);
        task.cancel();
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse completeTask(
            Long taskId,
            Long requesterUserId,
            Role requesterRole
    ) {
        Task task = findTask(taskId);
        ensureTaskAccess(task, requesterUserId, requesterRole);
        ensureTransitionAllowed(task.getStatus(), TaskStatus.COMPLETED);
        if (task.getAssignedEmployee() == null) {
            throw new ConflictException("A task must be assigned before completion.");
        }
        task.complete(LocalDateTime.now());
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse assignTask(Long taskId, Long employeeId) {
        Task task = findTask(taskId);
        ensureNotTerminal(task);

        if (employeeId == null) {
            if (task.getStatus() == TaskStatus.IN_PROGRESS) {
                throw new ConflictException("An in-progress task cannot be unassigned.");
            }
            task.assign(null);
        } else {
            task.assign(findActiveEmployee(employeeId));
        }
        return toResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> listAssignedTasks(
            Long employeeId,
            TaskStatus status,
            TaskPriority priority,
            Boolean overdue,
            PageCriteria pagination,
            Long requesterUserId,
            Role requesterRole
    ) {
        Employee employee = findEmployee(employeeId);
        if (requesterRole != Role.ADMIN) {
            User user = employee.getUser();
            if (requesterRole != Role.STAFF
                    || user == null
                    || !user.getId().equals(requesterUserId)) {
                throw new ForbiddenException("Assigned task access is forbidden.");
            }
        }
        return search(new TaskSearchCriteria(null, status, priority, employeeId, null, null, overdue), pagination);
    }

    private PagedResponse<TaskResponse> search(TaskSearchCriteria criteria, PageCriteria pagination) {
        Page<Task> tasks = taskRepository.search(
                criteria,
                LocalDateTime.now(),
                TERMINAL_STATUSES,
                pageRequest(pagination.page(), pagination.size(), pagination.sort())
        );
        return new PagedResponse<>(
                tasks.getContent().stream().map(this::toResponse).toList(),
                tasks.getNumber(),
                tasks.getSize(),
                tasks.getTotalElements(),
                tasks.getTotalPages(),
                tasks.isLast()
        );
    }

    private void ensureTaskAccess(Task task, Long requesterUserId, Role requesterRole) {
        if (requesterRole == Role.ADMIN) {
            return;
        }
        Employee assignedEmployee = task.getAssignedEmployee();
        User user = assignedEmployee == null ? null : assignedEmployee.getUser();
        if (requesterRole != Role.STAFF
                || user == null
                || !user.getId().equals(requesterUserId)) {
            throw new ForbiddenException("Task access is forbidden.");
        }
    }

    private void validateStatusAndAssignee(TaskStatus status, Employee employee) {
        if ((status == TaskStatus.ASSIGNED
                || status == TaskStatus.IN_PROGRESS
                || status == TaskStatus.COMPLETED)
                && employee == null) {
            throw new ConflictException(status + " tasks require an assignee.");
        }
        if (status == TaskStatus.OPEN && employee != null) {
            throw new ConflictException("OPEN tasks cannot have an assignee.");
        }
    }

    private void ensureTerminalTransitionPreservesAssignee(
            Task task,
            TaskStatus requestedStatus,
            Employee requestedEmployee
    ) {
        if (!TERMINAL_STATUSES.contains(requestedStatus)
                || task.getAssignedEmployee() == null) {
            return;
        }
        if (requestedEmployee == null
                || !task.getAssignedEmployee().getId().equals(requestedEmployee.getId())) {
            throw new ConflictException(
                    "Terminal transitions must preserve the assigned employee history."
            );
        }
    }

    private void ensureTransitionAllowed(TaskStatus current, TaskStatus requested) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(requested)) {
            throw new ConflictException(
                    "Task transition from " + current + " to " + requested + " is not allowed."
            );
        }
    }

    private void ensureNotTerminal(Task task) {
        if (TERMINAL_STATUSES.contains(task.getStatus())) {
            throw new ConflictException("Completed or cancelled tasks are terminal.");
        }
    }

    private Task findTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Task not found with id " + taskId + "."
                ));
    }

    private Department findActiveDepartment(Long departmentId) {
        Department department = departmentRepository.findByIdForUpdate(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department not found with id " + departmentId + "."
                ));
        if (!Boolean.TRUE.equals(department.getActive())) {
            throw new ConflictException("Tasks require an active department.");
        }
        return department;
    }

    private Employee findOptionalActiveEmployee(Long employeeId) {
        return employeeId == null ? null : findActiveEmployee(employeeId);
    }

    private Employee findActiveEmployee(Long employeeId) {
        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with id " + employeeId + "."
                ));
        if (employee.getStatus() != EmployeeStatus.ACTIVE
                || !Boolean.TRUE.equals(employee.getDepartment().getActive())) {
            throw new ConflictException("Tasks can be assigned only to active employees.");
        }
        return employee;
    }

    private Employee findEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with id " + employeeId + "."
                ));
    }

    private Room findOptionalActiveRoom(Long roomId) {
        if (roomId == null) {
            return null;
        }
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Room not found with id " + roomId + "."
                ));
        if (!Boolean.TRUE.equals(room.getActive())) {
            throw new ConflictException("Tasks cannot reference an inactive room.");
        }
        return room;
    }

    private Event findOptionalEligibleEvent(Long eventId) {
        if (eventId == null) {
            return null;
        }
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found with id " + eventId + "."
                ));
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("Tasks cannot reference a cancelled event.");
        }
        return event;
    }

    private void validateNewDueAt(LocalDateTime dueAt, LocalDateTime now) {
        if (dueAt != null && dueAt.isBefore(now)) {
            throw new BadRequestException("Due time must be current or future.");
        }
    }

    private void validateOptionalPositiveId(Long id, String label) {
        if (id != null && id <= 0) {
            throw new BadRequestException(label + " must be positive.");
        }
    }

    private PageRequest pageRequest(int page, int size, String sortValue) {
        if (page < 0) {
            throw new BadRequestException("Page must be zero or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size must be between 1 and 100.");
        }
        String[] sortParts = sortValue == null || sortValue.isBlank()
                ? new String[]{"createdAt", "desc"}
                : sortValue.split(",", -1);
        if (sortParts.length > 2 || !ALLOWED_SORT_FIELDS.contains(sortParts[0])) {
            throw new BadRequestException("Task sort is invalid.");
        }
        Sort.Direction direction;
        try {
            direction = sortParts.length == 1
                    ? Sort.Direction.ASC
                    : Sort.Direction.fromString(sortParts[1]);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Task sort direction is invalid.");
        }
        return PageRequest.of(page, size, Sort.by(direction, sortParts[0]));
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

    private TaskResponse toResponse(Task task) {
        Department department = task.getDepartment();
        Employee employee = task.getAssignedEmployee();
        Room room = task.getRoom();
        Event event = task.getEvent();
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                new DepartmentSummary(department.getId(), department.getName()),
                employee == null
                        ? null
                        : new TaskEmployeeSummary(employee.getId(), employee.getName()),
                room == null ? null : new TaskRoomSummary(room.getId(), room.getRoomNumber()),
                event == null
                        ? null
                        : new TaskEventSummary(
                                event.getId(),
                                event.getTitle(),
                                event.getEventDateTime(),
                                event.getStatus()
                        ),
                task.getCreatedAt(),
                task.getDueAt(),
                task.getCompletedAt(),
                task.getUpdatedAt()
        );
    }
}

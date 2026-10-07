package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.AuthDtos.DepartmentSummary;
import com.rockey.hospitality.dto.CommonDtos.PageCriteria;
import com.rockey.hospitality.dto.CommonDtos.PagedResponse;
import com.rockey.hospitality.dto.TaskDtos.CreateTaskRequest;
import com.rockey.hospitality.dto.TaskDtos.TaskEmployeeSummary;
import com.rockey.hospitality.dto.TaskDtos.TaskEventSummary;
import com.rockey.hospitality.dto.TaskDtos.TaskResponse;
import com.rockey.hospitality.dto.TaskDtos.TaskRoomSummary;
import com.rockey.hospitality.dto.TaskDtos.TaskSearchCriteria;
import com.rockey.hospitality.dto.TaskDtos.UpdateTaskRequest;
import com.rockey.hospitality.entity.Department;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.Event;
import com.rockey.hospitality.entity.Room;
import com.rockey.hospitality.entity.Task;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.exception.ApiException.BadRequestException;
import com.rockey.hospitality.exception.ApiException.ConflictException;
import com.rockey.hospitality.exception.ApiException.ForbiddenException;
import com.rockey.hospitality.exception.ApiException.ResourceNotFoundException;
import com.rockey.hospitality.repository.DepartmentRepository;
import com.rockey.hospitality.repository.EmployeeRepository;
import com.rockey.hospitality.repository.EventRepository;
import com.rockey.hospitality.repository.RoomRepository;
import com.rockey.hospitality.repository.TaskRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STUDY NOTE: A Service holds business rules and coordinates an application workflow.
 * Here, @Service lets Spring manage and inject this component; @Transactional groups database work so unchecked
 * failures roll back writes.
 * TaskService validates assignment/references and lifecycle changes while retaining completed or cancelled
 * work.
 * TaskController delegates here; Task, Department, Employee, Room and Event repositories provide the
 * persisted data through JPA/Hibernate.
 */
@Service
public class TaskService {

    // Transaction study key: Spring applies @Transactional when another component calls this managed service.
    // readOnly=true requests a read-oriented transaction; it keeps lazy reads and DTO mapping inside the
    // persistence boundary.
    // readOnly is not an authorization rule; repositories still run only after the service's scope checks.

    /**
     * OPEN, ASSIGNED, and IN_PROGRESS Tasks count as active work for guards and aggregates.
     */
    public static final Set<Task.Status> NON_TERMINAL_STATUSES = Set.of(
            Task.Status.OPEN,
            Task.Status.ASSIGNED,
            Task.Status.IN_PROGRESS
    );
    /**
     * COMPLETED and CANCELLED Task states are terminal and excluded from active-work conditions.
     */
    public static final Set<Task.Status> TERMINAL_STATUSES = Set.of(
            Task.Status.COMPLETED,
            Task.Status.CANCELLED
    );
    /**
     * Maximum of 100 rows per requested page, shared by this service's pagination checks.
     */
    private static final int MAX_PAGE_SIZE = 100;
    /**
     * Allowlist of sortable persisted fields, rejecting arbitrary property paths from request input.
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "title",
            "status",
            "priority",
            "dueAt",
            "createdAt",
            "updatedAt"
    );
    /**
     * Explicit allowed next-status map used to reject skipped or terminal lifecycle changes.
     */
    private static final Map<Task.Status, Set<Task.Status>> ALLOWED_TRANSITIONS = Map.of(
            Task.Status.OPEN,
            Set.of(Task.Status.ASSIGNED, Task.Status.CANCELLED),
            Task.Status.ASSIGNED,
            Set.of(
                    Task.Status.OPEN,
                    Task.Status.IN_PROGRESS,
                    Task.Status.COMPLETED,
                    Task.Status.CANCELLED
            ),
            Task.Status.IN_PROGRESS,
            Set.of(Task.Status.COMPLETED, Task.Status.CANCELLED),
            Task.Status.COMPLETED,
            Set.of(),
            Task.Status.CANCELLED,
            Set.of()
    );

    /**
     * Injected TaskRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final TaskRepository taskRepository;
    /**
     * Injected DepartmentRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final DepartmentRepository departmentRepository;
    /**
     * Injected EmployeeRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final EmployeeRepository employeeRepository;
    /**
     * Injected RoomRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final RoomRepository roomRepository;
    /**
     * Injected EventRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final EventRepository eventRepository;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
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

    /**
     * Validates optional reference IDs and delegates filter and page execution to the shared search helper.
     */
    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> listTasks(TaskSearchCriteria criteria, PageCriteria pagination) {
        validateOptionalPositiveId(criteria.departmentId(), "Department filter");
        validateOptionalPositiveId(criteria.assignedEmployeeId(), "Employee filter");
        validateOptionalPositiveId(criteria.roomId(), "Room filter");
        validateOptionalPositiveId(criteria.eventId(), "Event filter");
        return search(criteria, pagination);
    }

    /**
     * Checks due time and locks eligible Department, Employee, and Room references before saving new work.
     * The optional Event must exist and cannot be cancelled.
     */
    @Transactional
    public TaskResponse createTask(CreateTaskRequest request) {
        // Locked reference checks prevent deactivation racing the creation of new work.
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

    /**
     * Loads a Task and allows ADMIN oversight or the linked STAFF assignee's own read.
     */
    @Transactional(readOnly = true)
    public TaskResponse getTask(Long taskId, Long requesterUserId, User.Role requesterRole) {
        Task task = findTask(taskId);
        ensureTaskAccess(task, requesterUserId, requesterRole);
        return toResponse(task);
    }

    /**
     * Validates references and lifecycle transitions before replacing non-terminal Task details.
     * Terminal transitions preserve assignee history and set completion time only for COMPLETED.
     */
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

        Task.Status requestedStatus = request.getStatus();
        if (task.getStatus() != requestedStatus) {
            ensureTransitionAllowed(task.getStatus(), requestedStatus);
        }
        validateStatusAndAssignee(requestedStatus, employee);
        ensureTerminalTransitionPreservesAssignee(task, requestedStatus, employee);

        LocalDateTime completedAt = requestedStatus == Task.Status.COMPLETED ? now : null;
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

    /**
     * Transitions eligible work to CANCELLED without deleting its relationship history.
     */
    @Transactional
    public TaskResponse cancelTask(Long taskId) {
        Task task = findTask(taskId);
        ensureNotTerminal(task);
        ensureTransitionAllowed(task.getStatus(), Task.Status.CANCELLED);
        task.cancel();
        return toResponse(taskRepository.save(task));
    }

    /**
     * Checks access, allowed transition, and an existing assignee before storing COMPLETED with the current server time.
     */
    @Transactional
    public TaskResponse completeTask(
            Long taskId,
            Long requesterUserId,
            User.Role requesterRole
    ) {
        Task task = findTask(taskId);
        ensureTaskAccess(task, requesterUserId, requesterRole);
        ensureTransitionAllowed(task.getStatus(), Task.Status.COMPLETED);
        if (task.getAssignedEmployee() == null) {
            throw new ConflictException("A task must be assigned before completion.");
        }
        task.complete(LocalDateTime.now());
        return toResponse(taskRepository.save(task));
    }

    /**
     * Assigns an eligible active Employee or removes an assignment on non-terminal work.
     * IN_PROGRESS Tasks cannot be unassigned.
     */
    @Transactional
    public TaskResponse assignTask(Long taskId, Long employeeId) {
        Task task = findTask(taskId);
        ensureNotTerminal(task);

        if (employeeId == null) {
            if (task.getStatus() == Task.Status.IN_PROGRESS) {
                throw new ConflictException("An in-progress task cannot be unassigned.");
            }
            task.assign(null);
        } else {
            task.assign(findActiveEmployee(employeeId));
        }
        return toResponse(taskRepository.save(task));
    }

    /**
     * Pages one Employee's tasks for ADMIN or that Employee's linked STAFF User only.
     */
    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> listAssignedTasks(
            Long employeeId,
            Task.Status status,
            Task.Priority priority,
            Boolean overdue,
            PageCriteria pagination,
            Long requesterUserId,
            User.Role requesterRole
    ) {
        Employee employee = findEmployee(employeeId);
        if (requesterRole != User.Role.ADMIN) {
            User user = employee.getUser();
            if (requesterRole != User.Role.STAFF
                    || user == null
                    || !user.getId().equals(requesterUserId)) {
                throw new ForbiddenException("Assigned task access is forbidden.");
            }
        }
        return search(new TaskSearchCriteria(null, status, priority, employeeId, null, null, overdue), pagination);
    }

    /**
     * Uses one current time for overdue filtering and maps the selected page to safe Task DTOs.
     */
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

    /**
     * Allows ADMIN or the Task's linked STAFF assignee, not every employee in the same Department.
     */
    private void ensureTaskAccess(Task task, Long requesterUserId, User.Role requesterRole) {
        // STAFF may read only their own assignment; sharing a Department is not ownership.
        if (requesterRole == User.Role.ADMIN) {
            return;
        }
        Employee assignedEmployee = task.getAssignedEmployee();
        User user = assignedEmployee == null ? null : assignedEmployee.getUser();
        if (requesterRole != User.Role.STAFF
                || user == null
                || !user.getId().equals(requesterUserId)) {
            throw new ForbiddenException("Task access is forbidden.");
        }
    }

    /**
     * Requires an assignee for ASSIGNED, IN_PROGRESS, and COMPLETED work, and no assignee for OPEN work.
     */
    private void validateStatusAndAssignee(Task.Status status, Employee employee) {
        if ((status == Task.Status.ASSIGNED
                || status == Task.Status.IN_PROGRESS
                || status == Task.Status.COMPLETED)
                && employee == null) {
            throw new ConflictException(status + " tasks require an assignee.");
        }
        if (status == Task.Status.OPEN && employee != null) {
            throw new ConflictException("OPEN tasks cannot have an assignee.");
        }
    }

    /**
     * Prevents removing or replacing an existing assignee while completing or cancelling a Task.
     */
    private void ensureTerminalTransitionPreservesAssignee(
            Task task,
            Task.Status requestedStatus,
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

    /**
     * Rejects status changes not present in the Task lifecycle transition map.
     */
    private void ensureTransitionAllowed(Task.Status current, Task.Status requested) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(requested)) {
            throw new ConflictException(
                    "Task transition from " + current + " to " + requested + " is not allowed."
            );
        }
    }

    /**
     * Prevents further edits or reassignment of completed or cancelled work.
     */
    private void ensureNotTerminal(Task task) {
        if (TERMINAL_STATUSES.contains(task.getStatus())) {
            throw new ConflictException("Completed or cancelled tasks are terminal.");
        }
    }

    /**
     * Centralizes Task lookup and its 404 error.
     */
    private Task findTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Task not found with id " + taskId + "."
                ));
    }

    /**
     * Write-locks the required Department and rejects inactive destinations for Task creation or editing.
     */
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

    /**
     * Preserves a null assignment or delegates to the locked eligibility check.
     */
    private Employee findOptionalActiveEmployee(Long employeeId) {
        return employeeId == null ? null : findActiveEmployee(employeeId);
    }

    /**
     * Write-locks the Employee and requires both it and its Department to be active before assignment.
     */
    private Employee findActiveEmployee(Long employeeId) {
        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with id " + employeeId + "."
                ));
        if (employee.getStatus() != Employee.Status.ACTIVE
                || !Boolean.TRUE.equals(employee.getDepartment().getActive())) {
            throw new ConflictException("Tasks can be assigned only to active employees.");
        }
        return employee;
    }

    /**
     * Loads the Employee used for assigned-task listing or returns the canonical 404 error.
     */
    private Employee findEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with id " + employeeId + "."
                ));
    }

    /**
     * Preserves an omitted Room or write-locks an active Room so new work cannot race deactivation.
     */
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

    /**
     * Preserves an omitted Event; otherwise requires an existing, non-cancelled Event reference.
     */
    private Event findOptionalEligibleEvent(Long eventId) {
        if (eventId == null) {
            return null;
        }
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found with id " + eventId + "."
                ));
        if (event.getStatus() == Event.Status.CANCELLED) {
            throw new ConflictException("Tasks cannot reference a cancelled event.");
        }
        return event;
    }

    /**
     * Allows no due time or a current/future time when a Task's due date is created or changed.
     */
    private void validateNewDueAt(LocalDateTime dueAt, LocalDateTime now) {
        if (dueAt != null && dueAt.isBefore(now)) {
            throw new BadRequestException("Due time must be current or future.");
        }
    }

    /**
     * Rejects zero or negative IDs only when an optional filter was supplied.
     */
    private void validateOptionalPositiveId(Long id, String label) {
        if (id != null && id <= 0) {
            throw new BadRequestException(label + " must be positive.");
        }
    }

    /**
     * Validates zero-based page, size 1–100, and an allowlisted sort field and direction.
     * Omitted sorting uses createdAt descending, preventing arbitrary property paths.
     */
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

    /**
     * Converts blank descriptions to null and trims meaningful text.
     */
    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

    /**
     * Maps optional relationships to shallow summaries and retains Task lifecycle timestamps.
     * No User credentials or entity graph are returned.
     */
    private TaskResponse toResponse(Task task) {
        // Return shallow DTOs rather than exposing entity relationships or login credentials to React.
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

package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.task.TaskSearchCriteria;
import com.rockey.hospitality.dto.common.PageCriteria;
import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.dto.task.CreateTaskRequest;
import com.rockey.hospitality.dto.task.TaskResponse;
import com.rockey.hospitality.dto.task.UpdateTaskRequest;
import com.rockey.hospitality.entity.Department;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.EmployeeStatus;
import com.rockey.hospitality.entity.Event;
import com.rockey.hospitality.entity.EventStatus;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.Room;
import com.rockey.hospitality.entity.RoomStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private EventRepository eventRepository;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(
                taskRepository,
                departmentRepository,
                employeeRepository,
                roomRepository,
                eventRepository
        );
    }

    @Test
    void createUnassignedTaskStartsOpenWithDefaultPriority() {
        Department department = department(3L, true);
        CreateTaskRequest request = createRequest();
        request.setPriority(null);
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(department));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            ReflectionTestUtils.setField(task, "id", 41L);
            return task;
        });

        TaskResponse response = taskService.createTask(request);

        assertThat(response.getStatus()).isEqualTo(TaskStatus.OPEN);
        assertThat(response.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(response.getAssignedEmployee()).isNull();
        assertThat(response.getRoom()).isNull();
        assertThat(response.getTitle()).isEqualTo("Inspect room");
    }

    @Test
    void createWithActiveEmployeeAndRoomStartsAssigned() {
        Department department = department(3L, true);
        Employee employee = employee(12L, 21L, department, EmployeeStatus.ACTIVE);
        Room room = room(218L, true);
        CreateTaskRequest request = createRequest();
        request.setAssignedEmployeeId(12L);
        request.setRoomId(218L);
        request.setPriority(TaskPriority.HIGH);
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(department));
        when(employeeRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(employee));
        when(roomRepository.findByIdForUpdate(218L)).thenReturn(Optional.of(room));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.createTask(request);

        assertThat(response.getStatus()).isEqualTo(TaskStatus.ASSIGNED);
        assertThat(response.getAssignedEmployee().getId()).isEqualTo(12L);
        assertThat(response.getRoom().getId()).isEqualTo(218L);
        assertThat(response.getPriority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    void createLinksEligibleEventAndReturnsShallowSummary() {
        Department department = department(3L, true);
        Event event = event(7L, EventStatus.OPEN);
        CreateTaskRequest request = createRequest();
        request.setEventId(7L);
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(department));
        when(eventRepository.findById(7L)).thenReturn(Optional.of(event));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.createTask(request);

        assertThat(response.getEvent()).isNotNull();
        assertThat(response.getEvent().getId()).isEqualTo(7L);
        assertThat(response.getEvent().getStatus()).isEqualTo(EventStatus.OPEN);
    }

    @Test
    void createRejectsCancelledEventButAllowsNullEvent() {
        Department department = department(3L, true);
        CreateTaskRequest cancelledRequest = createRequest();
        cancelledRequest.setEventId(7L);
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(department));
        when(eventRepository.findById(7L))
                .thenReturn(Optional.of(event(7L, EventStatus.CANCELLED)));

        assertThatThrownBy(() -> taskService.createTask(cancelledRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Tasks cannot reference a cancelled event.");

        CreateTaskRequest noEventRequest = createRequest();
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(taskService.createTask(noEventRequest).getEvent()).isNull();
    }

    @Test
    void createRejectsPastDueDateBeforeResolvingReferences() {
        CreateTaskRequest request = createRequest();
        request.setDueAt(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> taskService.createTask(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Due time must be current or future.");

        verify(departmentRepository, never()).findById(any());
    }

    @Test
    void createRejectsInactiveDepartmentEmployeeAndRoom() {
        CreateTaskRequest request = createRequest();
        Department inactiveDepartment = department(3L, false);
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(inactiveDepartment));
        assertThatThrownBy(() -> taskService.createTask(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Tasks require an active department.");

        Department activeDepartment = department(3L, true);
        request.setAssignedEmployeeId(12L);
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(activeDepartment));
        when(employeeRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(employee(
                12L,
                21L,
                activeDepartment,
                EmployeeStatus.INACTIVE
        )));
        assertThatThrownBy(() -> taskService.createTask(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Tasks can be assigned only to active employees.");

        request.setAssignedEmployeeId(null);
        request.setRoomId(218L);
        when(roomRepository.findByIdForUpdate(218L)).thenReturn(Optional.of(room(218L, false)));
        assertThatThrownBy(() -> taskService.createTask(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Tasks cannot reference an inactive room.");
    }

    @Test
    void createReportsMissingReferences() {
        CreateTaskRequest request = createRequest();
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.createTask(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Department not found with id 3.");
    }

    @Test
    void listPassesAllFiltersPaginationAndOverdueTrue() {
        Department department = department(3L, true);
        Task task = task(41L, TaskStatus.ASSIGNED, department,
                employee(12L, 21L, department, EmployeeStatus.ACTIVE), room(218L, true));
        PageRequest pageRequest = PageRequest.of(
                1,
                5,
                Sort.by(Sort.Direction.ASC, "dueAt")
        );
        when(taskRepository.search(eq(new TaskSearchCriteria(3L, TaskStatus.ASSIGNED, TaskPriority.HIGH, 12L, 218L, 7L, true)), any(LocalDateTime.class), eq(TaskService.TERMINAL_STATUSES), eq(pageRequest))).thenReturn(new PageImpl<>(List.of(task), pageRequest, 7));

        PagedResponse<TaskResponse> response = taskService.listTasks(new TaskSearchCriteria(3L, TaskStatus.ASSIGNED, TaskPriority.HIGH, 12L, 218L, 7L, true), new PageCriteria(1, 5, "dueAt,asc"));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getTotalElements()).isEqualTo(6);
        assertThat(response.getTotalPages()).isEqualTo(2);
    }

    @Test
    void listPreservesOverdueFalseAndOmittedSemantics() {
        PageRequest pageRequest = PageRequest.of(
                0,
                20,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        when(taskRepository.search(any(TaskSearchCriteria.class), any(LocalDateTime.class), eq(TaskService.TERMINAL_STATUSES), eq(pageRequest))).thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        taskService.listTasks(new TaskSearchCriteria(null, null, null, null, null, null, false), new PageCriteria(0, 20, "createdAt,desc"));
        taskService.listTasks(new TaskSearchCriteria(null, null, null, null, null, null, null), new PageCriteria(0, 20, "createdAt,desc"));

        ArgumentCaptor<TaskSearchCriteria> overdueCaptor = ArgumentCaptor.forClass(TaskSearchCriteria.class);
        verify(taskRepository, times(2)).search(overdueCaptor.capture(), any(LocalDateTime.class), eq(TaskService.TERMINAL_STATUSES), eq(pageRequest));
        assertThat(overdueCaptor.getAllValues()).extracting(TaskSearchCriteria::overdue).containsExactly(false, null);
    }

    @Test
    void listRejectsInvalidIdsPaginationAndSort() {
        TaskSearchCriteria filterCriteria8 = new TaskSearchCriteria(0L, null, null, null, null, null, null);
        PageCriteria paginationCriteria9 = new PageCriteria(0, 20, "createdAt,desc");
        assertThatThrownBy(() -> taskService.listTasks(filterCriteria8, paginationCriteria9)).isInstanceOf(BadRequestException.class);
        TaskSearchCriteria filterCriteria6 = new TaskSearchCriteria(null, null, null, null, null, null, null);
        PageCriteria paginationCriteria7 = new PageCriteria(-1, 20, "createdAt,desc");
        assertThatThrownBy(() -> taskService.listTasks(filterCriteria6, paginationCriteria7)).isInstanceOf(BadRequestException.class);
        TaskSearchCriteria filterCriteria4 = new TaskSearchCriteria(null, null, null, null, null, null, null);
        PageCriteria paginationCriteria5 = new PageCriteria(0, 101, "createdAt,desc");
        assertThatThrownBy(() -> taskService.listTasks(filterCriteria4, paginationCriteria5)).isInstanceOf(BadRequestException.class);
        TaskSearchCriteria filterCriteria2 = new TaskSearchCriteria(null, null, null, null, null, null, null);
        PageCriteria paginationCriteria3 = new PageCriteria(0, 20, "passwordHash,asc");
        assertThatThrownBy(() -> taskService.listTasks(filterCriteria2, paginationCriteria3)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void getAllowsAdminAndAssignedStaffButRejectsOtherStaff() {
        Department department = department(3L, true);
        Employee assigned = employee(12L, 21L, department, EmployeeStatus.ACTIVE);
        Task task = task(41L, TaskStatus.ASSIGNED, department, assigned, null);
        when(taskRepository.findById(41L)).thenReturn(Optional.of(task));

        assertThat(taskService.getTask(41L, 3L, Role.ADMIN).getId()).isEqualTo(41L);
        assertThat(taskService.getTask(41L, 21L, Role.STAFF).getId()).isEqualTo(41L);
        assertThatThrownBy(() -> taskService.getTask(41L, 22L, Role.STAFF))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getRejectsMissingTask() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTask(99L, 3L, Role.ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void updatePermitsCanonicalTransitions(TaskStatus current, TaskStatus requested) {
        Department department = department(3L, true);
        Employee assigned = current == TaskStatus.OPEN
                ? null
                : employee(12L, 21L, department, EmployeeStatus.ACTIVE);
        Task task = task(41L, current, department, assigned, null);
        UpdateTaskRequest request = updateRequest(requested);
        Employee requestedEmployee = requested == TaskStatus.OPEN
                || (current == TaskStatus.OPEN && requested == TaskStatus.CANCELLED)
                ? null
                : assigned == null
                ? employee(12L, 21L, department, EmployeeStatus.ACTIVE)
                : assigned;
        request.setAssignedEmployeeId(
                requestedEmployee == null ? null : requestedEmployee.getId()
        );
        when(taskRepository.findById(41L)).thenReturn(Optional.of(task));
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(department));
        if (requestedEmployee != null) {
            when(employeeRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(requestedEmployee));
        }
        when(taskRepository.save(task)).thenReturn(task);

        TaskResponse response = taskService.updateTask(41L, request);

        assertThat(response.getStatus()).isEqualTo(requested);
        if (requested == TaskStatus.COMPLETED) {
            assertThat(response.getCompletedAt()).isNotNull();
        } else {
            assertThat(response.getCompletedAt()).isNull();
        }
    }

    @Test
    void updateRejectsInvalidTransitionAndTerminalMutation() {
        Department department = department(3L, true);
        Employee employee = employee(12L, 21L, department, EmployeeStatus.ACTIVE);
        Task open = task(41L, TaskStatus.OPEN, department, null, null);
        UpdateTaskRequest invalid = updateRequest(TaskStatus.COMPLETED);
        invalid.setAssignedEmployeeId(12L);
        when(taskRepository.findById(41L)).thenReturn(Optional.of(open));
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(department));
        when(employeeRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> taskService.updateTask(41L, invalid))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("OPEN to COMPLETED");

        Task completed = task(42L, TaskStatus.COMPLETED, department, employee, null);
        when(taskRepository.findById(42L)).thenReturn(Optional.of(completed));
        UpdateTaskRequest request = updateRequest(TaskStatus.COMPLETED);
        assertThatThrownBy(() -> taskService.updateTask(42L, request)).isInstanceOf(ConflictException.class)
                .hasMessage("Completed or cancelled tasks are terminal.");
    }

    @Test
    void updateRejectsChangedPastDueDateButAllowsExistingOverdueDate() {
        Department department = department(3L, true);
        Task task = task(41L, TaskStatus.OPEN, department, null, null);
        LocalDateTime existingPastDue = LocalDateTime.now().minusDays(1);
        ReflectionTestUtils.setField(task, "dueAt", existingPastDue);
        UpdateTaskRequest request = updateRequest(TaskStatus.OPEN);
        request.setDueAt(existingPastDue);
        when(taskRepository.findById(41L)).thenReturn(Optional.of(task));
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(department));
        when(taskRepository.save(task)).thenReturn(task);

        assertThat(taskService.updateTask(41L, request).getDueAt())
                .isEqualTo(existingPastDue);

        request.setDueAt(existingPastDue.minusHours(1));
        assertThatThrownBy(() -> taskService.updateTask(41L, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cancelPreservesHistoryAndRejectsTerminalTasks() {
        Department department = department(3L, true);
        Employee employee = employee(12L, 21L, department, EmployeeStatus.ACTIVE);
        Task task = task(41L, TaskStatus.ASSIGNED, department, employee, null);
        when(taskRepository.findById(41L)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        TaskResponse response = taskService.cancelTask(41L);

        assertThat(response.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(response.getAssignedEmployee().getId()).isEqualTo(12L);
        verify(taskRepository, never()).delete(any());

        assertThatThrownBy(() -> taskService.cancelTask(41L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void assignedStaffCompletesTaskAndServerSetsCompletionTime() {
        Department department = department(3L, true);
        Employee employee = employee(12L, 21L, department, EmployeeStatus.ACTIVE);
        Task task = task(41L, TaskStatus.IN_PROGRESS, department, employee, null);
        when(taskRepository.findById(41L)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        TaskResponse response = taskService.completeTask(41L, 21L, Role.STAFF);

        assertThat(response.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(response.getCompletedAt()).isNotNull();
    }

    @Test
    void completionRejectsOtherStaffAndOpenTask() {
        Department department = department(3L, true);
        Employee employee = employee(12L, 21L, department, EmployeeStatus.ACTIVE);
        Task assigned = task(41L, TaskStatus.ASSIGNED, department, employee, null);
        when(taskRepository.findById(41L)).thenReturn(Optional.of(assigned));
        assertThatThrownBy(() -> taskService.completeTask(41L, 22L, Role.STAFF))
                .isInstanceOf(ForbiddenException.class);

        Task open = task(42L, TaskStatus.OPEN, department, null, null);
        when(taskRepository.findById(42L)).thenReturn(Optional.of(open));
        assertThatThrownBy(() -> taskService.completeTask(42L, 3L, Role.ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("OPEN to COMPLETED");
    }

    @Test
    void assignAndUnassignApplyCanonicalStateChanges() {
        Department department = department(3L, true);
        Employee employee = employee(12L, 21L, department, EmployeeStatus.ACTIVE);
        Task task = task(41L, TaskStatus.OPEN, department, null, null);
        when(taskRepository.findById(41L)).thenReturn(Optional.of(task));
        when(employeeRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(employee));
        when(taskRepository.save(task)).thenReturn(task);

        assertThat(taskService.assignTask(41L, 12L).getStatus())
                .isEqualTo(TaskStatus.ASSIGNED);
        assertThat(taskService.assignTask(41L, null).getStatus())
                .isEqualTo(TaskStatus.OPEN);
    }

    @Test
    void assignRejectsInactiveEmployeeAndInProgressUnassignment() {
        Department department = department(3L, true);
        Employee inactive = employee(12L, 21L, department, EmployeeStatus.INACTIVE);
        Task open = task(41L, TaskStatus.OPEN, department, null, null);
        when(taskRepository.findById(41L)).thenReturn(Optional.of(open));
        when(employeeRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> taskService.assignTask(41L, 12L))
                .isInstanceOf(ConflictException.class);

        Task inProgress = task(
                42L,
                TaskStatus.IN_PROGRESS,
                department,
                employee(13L, 22L, department, EmployeeStatus.ACTIVE),
                null
        );
        when(taskRepository.findById(42L)).thenReturn(Optional.of(inProgress));
        assertThatThrownBy(() -> taskService.assignTask(42L, null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An in-progress task cannot be unassigned.");
    }

    @Test
    void assignedListAllowsSelfStaffAndAdminButRejectsOtherStaff() {
        Department department = department(3L, true);
        Employee employee = employee(12L, 21L, department, EmployeeStatus.ACTIVE);
        PageRequest pageRequest = PageRequest.of(
                0,
                20,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        when(employeeRepository.findById(12L)).thenReturn(Optional.of(employee));
        when(taskRepository.search(eq(new TaskSearchCriteria(null, TaskStatus.ASSIGNED, TaskPriority.HIGH, 12L, null, null, false)), any(LocalDateTime.class), eq(TaskService.TERMINAL_STATUSES), eq(pageRequest))).thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        taskService.listAssignedTasks(12L, TaskStatus.ASSIGNED, TaskPriority.HIGH, false, new PageCriteria(0, 20, "createdAt,desc"), 21L, Role.STAFF);
        taskService.listAssignedTasks(12L, TaskStatus.ASSIGNED, TaskPriority.HIGH, false, new PageCriteria(0, 20, "createdAt,desc"), 3L, Role.ADMIN);
        PageCriteria paginationCriteria1 = new PageCriteria(0, 20, "createdAt,desc");
        assertThatThrownBy(() -> taskService.listAssignedTasks(12L, null, null, null, paginationCriteria1, 22L, Role.STAFF)).isInstanceOf(ForbiddenException.class);
    }

    private static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(TaskStatus.OPEN, TaskStatus.ASSIGNED),
                Arguments.of(TaskStatus.OPEN, TaskStatus.CANCELLED),
                Arguments.of(TaskStatus.ASSIGNED, TaskStatus.OPEN),
                Arguments.of(TaskStatus.ASSIGNED, TaskStatus.IN_PROGRESS),
                Arguments.of(TaskStatus.ASSIGNED, TaskStatus.COMPLETED),
                Arguments.of(TaskStatus.ASSIGNED, TaskStatus.CANCELLED),
                Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED),
                Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED)
        );
    }

    private CreateTaskRequest createRequest() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("  Inspect room  ");
        request.setDescription("  Check readiness.  ");
        request.setDepartmentId(3L);
        request.setPriority(TaskPriority.MEDIUM);
        request.setDueAt(LocalDateTime.of(2030, 10, 4, 15, 0));
        return request;
    }

    private UpdateTaskRequest updateRequest(TaskStatus status) {
        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setTitle("Updated task");
        request.setDescription("Updated instructions");
        request.setDepartmentId(3L);
        request.setPriority(TaskPriority.HIGH);
        request.setStatus(status);
        request.setDueAt(LocalDateTime.of(2030, 10, 5, 15, 0));
        return request;
    }

    private Department department(Long id, boolean active) {
        Department department = new Department("Housekeeping", null);
        ReflectionTestUtils.setField(department, "id", id);
        if (!active) {
            department.deactivate();
        }
        return department;
    }

    private Employee employee(
            Long id,
            Long userId,
            Department department,
            EmployeeStatus status
    ) {
        User user = new User("Worker", "worker" + userId + "@example.test", "hash");
        ReflectionTestUtils.setField(user, "id", userId);
        user.provisionEmployeeAccess(Role.STAFF, department);
        Employee employee = new Employee(
                "Worker",
                "worker" + id + "@example.test",
                department,
                "Room Attendant",
                user
        );
        ReflectionTestUtils.setField(employee, "id", id);
        ReflectionTestUtils.setField(employee, "status", status);
        return employee;
    }

    private Room room(Long id, boolean active) {
        Room room = new Room("218", "STANDARD", 2, RoomStatus.READY, null);
        ReflectionTestUtils.setField(room, "id", id);
        ReflectionTestUtils.setField(room, "active", active);
        return room;
    }

    private Event event(Long id, EventStatus status) {
        Event event = new Event(
                "Leadership Conference",
                null,
                LocalDateTime.of(2030, 10, 10, 9, 0),
                "Ballroom A",
                120,
                status
        );
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    private Task task(
            Long id,
            TaskStatus status,
            Department department,
            Employee employee,
            Room room
    ) {
        Task task = new Task(
                "Inspect room",
                "Check readiness.",
                department,
                employee,
                room,
                TaskPriority.HIGH,
                LocalDateTime.of(2030, 10, 4, 15, 0)
        );
        ReflectionTestUtils.setField(task, "id", id);
        ReflectionTestUtils.setField(task, "status", status);
        if (status == TaskStatus.COMPLETED) {
            ReflectionTestUtils.setField(
                    task,
                    "completedAt",
                    LocalDateTime.of(2026, 10, 3, 12, 0)
            );
        }
        return task;
    }
}

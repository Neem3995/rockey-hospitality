package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.dto.employee.CreateEmployeeRequest;
import com.rockey.hospitality.dto.employee.EmployeeResponse;
import com.rockey.hospitality.dto.employee.UpdateEmployeeRequest;
import com.rockey.hospitality.entity.Department;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.EmployeeStatus;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.entity.UserStatus;
import com.rockey.hospitality.exception.BadRequestException;
import com.rockey.hospitality.exception.ConflictException;
import com.rockey.hospitality.exception.ForbiddenException;
import com.rockey.hospitality.exception.ResourceNotFoundException;
import com.rockey.hospitality.repository.DepartmentRepository;
import com.rockey.hospitality.repository.EmployeeRepository;
import com.rockey.hospitality.repository.UserRepository;
import com.rockey.hospitality.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TaskRepository taskRepository;

    private EmployeeService employeeService;

    @BeforeEach
    void setUp() {
        employeeService = new EmployeeService(
                employeeRepository,
                userRepository,
                departmentRepository,
                passwordEncoder,
                taskRepository
        );
    }

    @Test
    void createWithoutLoginPersistsNullableUserAndNoPlaceholderAccount() {
        Department department = department(3L, "Housekeeping", true);
        CreateEmployeeRequest request = createRequest(false);
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(department));
        when(employeeRepository.existsByEmailIgnoreCase("worker@example.test"))
                .thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            ReflectionTestUtils.setField(employee, "id", 12L);
            return employee;
        });

        EmployeeResponse response = employeeService.createEmployee(request);

        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(employeeCaptor.capture());
        assertThat(employeeCaptor.getValue().getUser()).isNull();
        assertThat(employeeCaptor.getValue().getEmail()).isEqualTo("worker@example.test");
        assertThat(response.getUserId()).isNull();
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void createWithLoginBuildsBcryptBackedStaffAccountAndConsistentDepartment() {
        Department department = department(3L, "Housekeeping", true);
        CreateEmployeeRequest request = createRequest(true);
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(department));
        when(employeeRepository.existsByEmailIgnoreCase("worker@example.test"))
                .thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("login@example.test")).thenReturn(false);
        when(passwordEncoder.encode("temporary-password")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 21L);
            return user;
        });
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            ReflectionTestUtils.setField(employee, "id", 12L);
            return employee;
        });

        EmployeeResponse response = employeeService.createEmployee(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("login@example.test");
        assertThat(savedUser.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(savedUser.getRole()).isEqualTo(Role.STAFF);
        assertThat(savedUser.getDepartment()).isSameAs(department);
        assertThat(response.getUserId()).isEqualTo(21L);
        assertThat(response.getDepartment().getId()).isEqualTo(3L);
    }

    @Test
    void createRejectsDuplicateEmployeeEmail() {
        CreateEmployeeRequest request = createRequest(false);
        when(departmentRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(department(3L, "Housekeeping", true)));
        when(employeeRepository.existsByEmailIgnoreCase("worker@example.test"))
                .thenReturn(true);

        assertThatThrownBy(() -> employeeService.createEmployee(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Employee email is already registered.");

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateLoginEmailBeforePersistingAccount() {
        CreateEmployeeRequest request = createRequest(true);
        when(departmentRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(department(3L, "Housekeeping", true)));
        when(userRepository.existsByEmailIgnoreCase("login@example.test")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.createEmployee(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Login email is already registered.");

        verify(userRepository, never()).save(any());
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void createRejectsInactiveDepartment() {
        CreateEmployeeRequest request = createRequest(false);
        when(departmentRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(department(3L, "Housekeeping", false)));

        assertThatThrownBy(() -> employeeService.createEmployee(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Employees cannot be assigned to an inactive department.");
    }

    @Test
    void createRejectsLoginFieldsWhenCreateLoginIsFalse() {
        CreateEmployeeRequest request = createRequest(false);
        request.setLoginEmail("login@example.test");

        assertThatThrownBy(() -> employeeService.createEmployee(request))
                .isInstanceOf(BadRequestException.class);

        verify(departmentRepository, never()).findById(any());
    }

    @Test
    void listUsesCombinedFiltersAndReturnsCanonicalPageMetadata() {
        Department department = department(3L, "Housekeeping", true);
        Employee employee = employee(12L, department, null, EmployeeStatus.ACTIVE);
        PageRequest expectedPage = PageRequest.of(
                1,
                5,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC,
                        "createdAt"
                )
        );
        when(employeeRepository.findByDepartmentIdAndStatus(
                3L,
                EmployeeStatus.ACTIVE,
                expectedPage
        )).thenReturn(new PageImpl<>(List.of(employee), expectedPage, 7));

        PagedResponse<EmployeeResponse> response = employeeService.listEmployees(
                3L,
                EmployeeStatus.ACTIVE,
                1,
                5,
                "createdAt,desc"
        );

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(5);
        assertThat(response.getTotalElements()).isEqualTo(6);
        assertThat(response.getTotalPages()).isEqualTo(2);
        assertThat(response.isLast()).isTrue();
    }

    @Test
    void listRejectsInvalidPaginationAndSort() {
        assertThatThrownBy(() -> employeeService.listEmployees(
                null,
                null,
                -1,
                20,
                "name,asc"
        )).isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> employeeService.listEmployees(
                null,
                null,
                0,
                101,
                "name,asc"
        )).isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> employeeService.listEmployees(
                null,
                null,
                0,
                20,
                "passwordHash,asc"
        )).isInstanceOf(BadRequestException.class);
    }

    @Test
    void getAllowsLinkedStaffToReadOwnEmployeeRecord() {
        Department department = department(3L, "Housekeeping", true);
        User user = user(21L, Role.STAFF, department);
        Employee employee = employee(12L, department, user, EmployeeStatus.ACTIVE);
        when(employeeRepository.findById(12L)).thenReturn(Optional.of(employee));

        EmployeeResponse response = employeeService.getEmployee(12L, 21L, Role.STAFF);

        assertThat(response.getId()).isEqualTo(12L);
        assertThat(response.getUserId()).isEqualTo(21L);
    }

    @Test
    void getRejectsStaffReadingAnotherEmployee() {
        Department department = department(3L, "Housekeeping", true);
        Employee employee = employee(
                12L,
                department,
                user(21L, Role.STAFF, department),
                EmployeeStatus.ACTIVE
        );
        when(employeeRepository.findById(12L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> employeeService.getEmployee(12L, 22L, Role.STAFF))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getRejectsMissingEmployee() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getEmployee(99L, 1L, Role.ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateTransfersLinkedEmployeeAndUserInOneOperation() {
        Department current = department(3L, "Housekeeping", true);
        Department target = department(4L, "Maintenance", true);
        User user = user(21L, Role.STAFF, current);
        Employee employee = employee(12L, current, user, EmployeeStatus.ACTIVE);
        UpdateEmployeeRequest request = updateRequest(4L, EmployeeStatus.ACTIVE);
        when(employeeRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(employee));
        when(departmentRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(target));
        when(employeeRepository.save(employee)).thenReturn(employee);
        when(userRepository.save(user)).thenReturn(user);

        EmployeeResponse response = employeeService.updateEmployee(12L, request);

        assertThat(employee.getDepartment()).isSameAs(target);
        assertThat(user.getDepartment()).isSameAs(target);
        assertThat(response.getDepartment().getId()).isEqualTo(4L);
        verify(userRepository).save(user);
        verify(employeeRepository).save(employee);
    }

    @Test
    void updateRejectsTransferToInactiveDepartment() {
        Department current = department(3L, "Housekeeping", true);
        Department target = department(4L, "Maintenance", false);
        Employee employee = employee(12L, current, null, EmployeeStatus.ACTIVE);
        when(employeeRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(employee));
        when(departmentRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(target));

        UpdateEmployeeRequest request = updateRequest(4L, EmployeeStatus.ACTIVE);
        assertThatThrownBy(() -> employeeService.updateEmployee(12L, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateDeactivationAlsoDisablesLinkedUserAndRevokesRefresh() {
        Department department = department(3L, "Housekeeping", true);
        User user = user(21L, Role.STAFF, department);
        user.replaceRefreshSession("refresh-hash", LocalDateTime.of(2026, 10, 10, 20, 0));
        Employee employee = employee(12L, department, user, EmployeeStatus.ACTIVE);
        when(employeeRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(employee));
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(department));
        when(employeeRepository.save(employee)).thenReturn(employee);
        when(userRepository.save(user)).thenReturn(user);

        employeeService.updateEmployee(
                12L,
                updateRequest(3L, EmployeeStatus.INACTIVE)
        );

        assertThat(employee.getStatus()).isEqualTo(EmployeeStatus.INACTIVE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(user.getRefreshTokenHash()).isNull();
        assertThat(user.getRefreshTokenExpiresAt()).isNull();
    }

    @Test
    void deactivatePreservesEmployeeAndDisablesLinkedAccount() {
        Department department = department(3L, "Housekeeping", true);
        User user = user(21L, Role.STAFF, department);
        Employee employee = employee(12L, department, user, EmployeeStatus.ACTIVE);
        when(employeeRepository.findDepartmentIdById(12L)).thenReturn(Optional.of(3L));
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(department));
        when(employeeRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);
        when(userRepository.save(user)).thenReturn(user);

        employeeService.deactivateEmployee(12L);

        assertThat(employee.getStatus()).isEqualTo(EmployeeStatus.INACTIVE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(employeeRepository, never()).delete(any());
    }

    @Test
    void deactivateUnlinkedEmployeeIsSafe() {
        Department department = department(3L, "Housekeeping", true);
        Employee employee = employee(12L, department, null, EmployeeStatus.ACTIVE);
        when(employeeRepository.findDepartmentIdById(12L)).thenReturn(Optional.of(3L));
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(department));
        when(employeeRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);

        employeeService.deactivateEmployee(12L);

        assertThat(employee.getStatus()).isEqualTo(EmployeeStatus.INACTIVE);
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateRejectsEmployeeWithNonTerminalAssignedTasks() {
        Department department = department(3L, "Housekeeping", true);
        Employee employee = employee(12L, department, null, EmployeeStatus.ACTIVE);
        when(employeeRepository.findDepartmentIdById(12L)).thenReturn(Optional.of(3L));
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(department));
        when(employeeRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(employee));
        when(taskRepository.existsByAssignedEmployeeIdAndStatusIn(
                12L,
                TaskService.NON_TERMINAL_STATUSES
        )).thenReturn(true);

        assertThatThrownBy(() -> employeeService.deactivateEmployee(12L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Employee cannot be deactivated while active tasks are assigned.");

        assertThat(employee.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
        verify(employeeRepository, never()).save(employee);
    }

    @Test
    void updateRejectsInactiveStatusWhenEmployeeHasNonTerminalTasks() {
        Department department = department(3L, "Housekeeping", true);
        Employee employee = employee(12L, department, null, EmployeeStatus.ACTIVE);
        when(employeeRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(employee));
        when(departmentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(department));
        when(taskRepository.existsByAssignedEmployeeIdAndStatusIn(
                12L,
                TaskService.NON_TERMINAL_STATUSES
        )).thenReturn(true);

        UpdateEmployeeRequest request = updateRequest(3L, EmployeeStatus.INACTIVE);
        assertThatThrownBy(() -> employeeService.updateEmployee(12L, request))
                .isInstanceOf(ConflictException.class);
    }

    private CreateEmployeeRequest createRequest(boolean createLogin) {
        CreateEmployeeRequest request = new CreateEmployeeRequest();
        request.setName("  Worker Name  ");
        request.setEmail(" WORKER@example.test ");
        request.setDepartmentId(3L);
        request.setJobRole("  Room Attendant  ");
        request.setCreateLogin(createLogin);
        if (createLogin) {
            request.setLoginEmail(" LOGIN@example.test ");
            request.setTemporaryPassword("temporary-password");
            request.setSecurityRole(Role.STAFF);
        }
        return request;
    }

    private UpdateEmployeeRequest updateRequest(Long departmentId, EmployeeStatus status) {
        UpdateEmployeeRequest request = new UpdateEmployeeRequest();
        request.setName("Updated Worker");
        request.setEmail("updated@example.test");
        request.setDepartmentId(departmentId);
        request.setJobRole("Senior Attendant");
        request.setStatus(status);
        return request;
    }

    private Department department(Long id, String name, boolean active) {
        Department department = new Department(name, null);
        ReflectionTestUtils.setField(department, "id", id);
        if (!active) {
            department.deactivate();
        }
        return department;
    }

    private User user(Long id, Role role, Department department) {
        User user = new User("Worker Name", "login@example.test", "bcrypt-hash");
        ReflectionTestUtils.setField(user, "id", id);
        user.provisionEmployeeAccess(role, department);
        return user;
    }

    private Employee employee(
            Long id,
            Department department,
            User user,
            EmployeeStatus status
    ) {
        Employee employee = new Employee(
                "Worker Name",
                "worker@example.test",
                department,
                "Room Attendant",
                user
        );
        ReflectionTestUtils.setField(employee, "id", id);
        ReflectionTestUtils.setField(employee, "status", status);
        return employee;
    }
}

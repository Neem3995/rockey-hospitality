package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.auth.DepartmentSummary;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.Locale;
import java.util.Set;

@Service
public class EmployeeService {
    private static final String EMPLOYEE_NOT_FOUND_PREFIX = "Employee not found with id ";

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name",
            "email",
            "jobRole",
            "status",
            "createdAt"
    );

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final TaskRepository taskRepository;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder,
            TaskRepository taskRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<EmployeeResponse> listEmployees(
            Long departmentId,
            EmployeeStatus status,
            int page,
            int size,
            String sort
    ) {
        PageRequest pageRequest = pageRequest(page, size, sort);
        Page<Employee> employees;
        if (departmentId != null && departmentId <= 0) {
            throw new BadRequestException("Department filter must be positive.");
        }
        if (departmentId != null && status != null) {
            employees = employeeRepository.findByDepartmentIdAndStatus(
                    departmentId,
                    status,
                    pageRequest
            );
        } else if (departmentId != null) {
            employees = employeeRepository.findByDepartmentId(departmentId, pageRequest);
        } else if (status != null) {
            employees = employeeRepository.findByStatus(status, pageRequest);
        } else {
            employees = employeeRepository.findAll(pageRequest);
        }

        return new PagedResponse<>(
                employees.getContent().stream().map(this::toResponse).toList(),
                employees.getNumber(),
                employees.getSize(),
                employees.getTotalElements(),
                employees.getTotalPages(),
                employees.isLast()
        );
    }

    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        validateProvisioningRequest(request);
        Department department = findActiveDepartment(request.getDepartmentId());
        String employeeEmail = normalizeEmail(request.getEmail());
        ensureEmployeeEmailAvailable(employeeEmail);

        User user = null;
        if (Boolean.TRUE.equals(request.getCreateLogin())) {
            String loginEmail = normalizeEmail(request.getLoginEmail());
            ensureUserEmailAvailable(loginEmail);
            user = new User(
                    request.getName().trim(),
                    loginEmail,
                    passwordEncoder.encode(request.getTemporaryPassword())
            );
            user.provisionEmployeeAccess(request.getSecurityRole(), department);
            user = userRepository.save(user);
        }

        Employee employee = new Employee(
                request.getName().trim(),
                employeeEmail,
                department,
                request.getJobRole().trim(),
                user
        );
        return toResponse(employeeRepository.save(employee));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployee(
            Long employeeId,
            Long requesterUserId,
            Role requesterRole
    ) {
        Employee employee = findEmployee(employeeId);
        if (requesterRole != Role.ADMIN) {
            User linkedUser = employee.getUser();
            if (requesterRole != Role.STAFF
                    || linkedUser == null
                    || !linkedUser.getId().equals(requesterUserId)) {
                throw new ForbiddenException("Employee record access is forbidden.");
            }
        }
        return toResponse(employee);
    }

    @Transactional
    public EmployeeResponse updateEmployee(
            Long employeeId,
            UpdateEmployeeRequest request
    ) {
        Department department = findDepartment(request.getDepartmentId());
        Employee employee = findEmployeeForUpdate(employeeId);
        boolean departmentChanged = !employee.getDepartment().getId().equals(department.getId());
        if ((departmentChanged || request.getStatus() == EmployeeStatus.ACTIVE)
                && !Boolean.TRUE.equals(department.getActive())) {
            throw new ConflictException("Employees cannot be assigned to an inactive department.");
        }
        if (request.getStatus() == EmployeeStatus.INACTIVE) {
            ensureNoActiveTasks(employeeId);
        }

        String email = normalizeEmail(request.getEmail());
        if (employeeRepository.existsByEmailIgnoreCaseAndIdNot(email, employeeId)) {
            throw new ConflictException("Employee email is already registered.");
        }

        employee.updateProfile(
                request.getName().trim(),
                email,
                department,
                request.getJobRole().trim(),
                request.getStatus()
        );
        synchronizeLinkedUser(employee, department, request.getStatus());
        return toResponse(employeeRepository.save(employee));
    }

    // The Department-ID routing lookup occurs before locking; guard reads must
    // see Task commits made while this transaction waited for that lock.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deactivateEmployee(Long employeeId) {
        // Match Task's Department -> Employee lock order; avoid a foreign-key
        // lock upgrade deadlock when assignment races with deactivation.
        Long departmentId = employeeRepository.findDepartmentIdById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EMPLOYEE_NOT_FOUND_PREFIX + employeeId + "."
                ));
        findDepartment(departmentId);
        Employee employee = findEmployeeForUpdate(employeeId);
        if (!employee.getDepartment().getId().equals(departmentId)) {
            throw new ConflictException("Employee department changed concurrently. Retry the operation.");
        }
        ensureNoActiveTasks(employeeId);
        employee.deactivate();
        synchronizeLinkedUser(employee, employee.getDepartment(), EmployeeStatus.INACTIVE);
        employeeRepository.save(employee);
    }

    private void ensureNoActiveTasks(Long employeeId) {
        if (taskRepository.existsByAssignedEmployeeIdAndStatusIn(
                employeeId,
                TaskService.NON_TERMINAL_STATUSES
        )) {
            throw new ConflictException(
                    "Employee cannot be deactivated while active tasks are assigned."
            );
        }
    }

    private void synchronizeLinkedUser(
            Employee employee,
            Department department,
            EmployeeStatus employeeStatus
    ) {
        User user = employee.getUser();
        if (user == null) {
            return;
        }
        user.synchronizeEmployeeDepartment(department);
        user.synchronizeEmployeeStatus(
                employeeStatus == EmployeeStatus.ACTIVE
                        ? UserStatus.ACTIVE
                        : UserStatus.INACTIVE
        );
        userRepository.save(user);
    }

    private Employee findEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EMPLOYEE_NOT_FOUND_PREFIX + employeeId + "."
                ));
    }

    private Employee findEmployeeForUpdate(Long employeeId) {
        return employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EMPLOYEE_NOT_FOUND_PREFIX + employeeId + "."
                ));
    }

    private Department findDepartment(Long departmentId) {
        return departmentRepository.findByIdForUpdate(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department not found with id " + departmentId + "."
                ));
    }

    private Department findActiveDepartment(Long departmentId) {
        Department department = findDepartment(departmentId);
        if (!Boolean.TRUE.equals(department.getActive())) {
            throw new ConflictException("Employees cannot be assigned to an inactive department.");
        }
        return department;
    }

    private void ensureEmployeeEmailAvailable(String email) {
        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Employee email is already registered.");
        }
    }

    private void ensureUserEmailAvailable(String email) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Login email is already registered.");
        }
    }

    private void validateProvisioningRequest(CreateEmployeeRequest request) {
        if (request.getCreateLogin() == null) {
            throw new BadRequestException("createLogin is required.");
        }
        boolean hasLoginEmail = request.getLoginEmail() != null
                && !request.getLoginEmail().isBlank();
        boolean hasTemporaryPassword = request.getTemporaryPassword() != null
                && !request.getTemporaryPassword().isBlank();
        if (!request.getCreateLogin()) {
            if (hasLoginEmail
                    || hasTemporaryPassword
                    || request.getSecurityRole() != null) {
                throw new BadRequestException(
                        "Login fields must be absent when createLogin is false."
                );
            }
            return;
        }
        if (!hasLoginEmail
                || !hasTemporaryPassword
                || (request.getSecurityRole() != Role.STAFF
                && request.getSecurityRole() != Role.ADMIN)) {
            throw new BadRequestException(
                    "A STAFF or ADMIN login email and temporary password are required."
            );
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
                ? new String[]{"name", "asc"}
                : sortValue.split(",", -1);
        if (sortParts.length > 2 || !ALLOWED_SORT_FIELDS.contains(sortParts[0])) {
            throw new BadRequestException("Employee sort is invalid.");
        }
        Sort.Direction direction;
        try {
            direction = sortParts.length == 1
                    ? Sort.Direction.ASC
                    : Sort.Direction.fromString(sortParts[1]);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Employee sort direction is invalid.");
        }
        return PageRequest.of(page, size, Sort.by(direction, sortParts[0]));
    }

    private EmployeeResponse toResponse(Employee employee) {
        User user = employee.getUser();
        Department department = employee.getDepartment();
        return new EmployeeResponse(
                employee.getId(),
                user == null ? null : user.getId(),
                employee.getName(),
                employee.getEmail(),
                new DepartmentSummary(department.getId(), department.getName()),
                employee.getJobRole(),
                employee.getStatus(),
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

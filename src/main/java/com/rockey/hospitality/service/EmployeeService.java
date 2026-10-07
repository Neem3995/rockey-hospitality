package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.AuthDtos.DepartmentSummary;
import com.rockey.hospitality.dto.CommonDtos.PagedResponse;
import com.rockey.hospitality.dto.EmployeeDtos.CreateEmployeeRequest;
import com.rockey.hospitality.dto.EmployeeDtos.EmployeeResponse;
import com.rockey.hospitality.dto.EmployeeDtos.UpdateEmployeeRequest;
import com.rockey.hospitality.entity.Department;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.exception.ApiException.BadRequestException;
import com.rockey.hospitality.exception.ApiException.ConflictException;
import com.rockey.hospitality.exception.ApiException.ForbiddenException;
import com.rockey.hospitality.exception.ApiException.ResourceNotFoundException;
import com.rockey.hospitality.repository.DepartmentRepository;
import com.rockey.hospitality.repository.EmployeeRepository;
import com.rockey.hospitality.repository.TaskRepository;
import com.rockey.hospitality.repository.UserRepository;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * STUDY NOTE: A Service holds business rules and coordinates an application workflow.
 * Here, @Service lets Spring manage and inject this component; @Transactional groups database work so unchecked
 * failures roll back writes.
 * EmployeeService validates profiles, provisions optional STAFF/ADMIN logins, synchronizes linked records
 * and guards deactivation.
 * EmployeeController delegates here; Employee, User, Department and Task repositories provide the persisted
 * data through JPA/Hibernate.
 */
@Service
public class EmployeeService {

    // Transaction study key: Spring applies @Transactional when another component calls this managed service.
    // readOnly=true requests a read-oriented transaction; it keeps lazy reads and DTO mapping inside the
    // persistence boundary.
    // readOnly is not an authorization rule; repositories still run only after the service's scope checks.
    // Employee and optional User changes must commit together so a failure cannot leave half-provisioned or
    // inconsistent records.
    /**
     * Shared prefix keeping profile lookup errors consistent.
     */
    private static final String EMPLOYEE_NOT_FOUND_PREFIX = "Employee not found with id ";

    /**
     * Maximum of 100 rows per requested page, shared by this service's pagination checks.
     */
    private static final int MAX_PAGE_SIZE = 100;
    /**
     * Allowlist of sortable persisted fields, rejecting arbitrary property paths from request input.
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name",
            "email",
            "jobRole",
            "status",
            "createdAt"
    );

    /**
     * Injected EmployeeRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final EmployeeRepository employeeRepository;
    /**
     * Injected UserRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final UserRepository userRepository;
    /**
     * Injected DepartmentRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final DepartmentRepository departmentRepository;
    /**
     * Shared BCrypt encoder for password creation and credential matching.
     */
    private final PasswordEncoder passwordEncoder;
    /**
     * Injected TaskRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final TaskRepository taskRepository;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
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

    /**
     * Chooses the repository query matching optional Department and status filters, then returns a validated page of safe profile DTOs.
     */
    @Transactional(readOnly = true)
    public PagedResponse<EmployeeResponse> listEmployees(
            Long departmentId,
            Employee.Status status,
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

    /**
     * Creates an active profile in a locked active Department and optionally provisions one new STAFF/ADMIN User with a BCrypt hash.
     * Without login provisioning the User link remains null.
     */
    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        // One transaction creates the optional login and profile together, never half an account.
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

    /**
     * Returns the requested profile to ADMIN or to the linked STAFF account itself.
     * An unrelated account cannot read the record.
     */
    @Transactional(readOnly = true)
    public EmployeeResponse getEmployee(
            Long employeeId,
            Long requesterUserId,
            User.Role requesterRole
    ) {
        Employee employee = findEmployee(employeeId);
        if (requesterRole != User.Role.ADMIN) {
            User linkedUser = employee.getUser();
            if (requesterRole != User.Role.STAFF
                    || linkedUser == null
                    || !linkedUser.getId().equals(requesterUserId)) {
                throw new ForbiddenException("Employee record access is forbidden.");
            }
        }
        return toResponse(employee);
    }

    /**
     * Locks Department then Employee, validates reassignment and deactivation, and updates the profile.
     * Linked User Department and status are synchronized in the same transaction.
     */
    @Transactional
    public EmployeeResponse updateEmployee(
            Long employeeId,
            UpdateEmployeeRequest request
    ) {
        Department department = findDepartment(request.getDepartmentId());
        // Match Task creation's Department -> Employee lock order before changing linked records.
        Employee employee = findEmployeeForUpdate(employeeId);
        boolean departmentChanged = !employee.getDepartment().getId().equals(department.getId());
        if ((departmentChanged || request.getStatus() == Employee.Status.ACTIVE)
                && !Boolean.TRUE.equals(department.getActive())) {
            throw new ConflictException("Employees cannot be assigned to an inactive department.");
        }
        if (request.getStatus() == Employee.Status.INACTIVE) {
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
        // The login's authorization Department must stay equal to the Employee's work Department.
        synchronizeLinkedUser(employee, department, request.getStatus());
        return toResponse(employeeRepository.save(employee));
    }

    // The Department-ID routing lookup occurs before locking; guard reads must
    // see Task commits made while this transaction waited for that lock.
    /**
     * Locks Department then Employee and rejects concurrent Department changes or active Task assignments.
     * Soft deactivation also disables a linked User and clears its refresh session.
     */
    // READ_COMMITTED lets post-lock checks observe preceding committed changes.
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
        synchronizeLinkedUser(employee, employee.getDepartment(), Employee.Status.INACTIVE);
        employeeRepository.save(employee);
    }

    /**
     * Blocks Employee deactivation while any assigned Task is OPEN, ASSIGNED, or IN_PROGRESS.
     */
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

    /**
     * Keeps an optional linked User's Department and status equal to its Employee profile.
     * No User is created for a profile that has no login.
     */
    private void synchronizeLinkedUser(
            Employee employee,
            Department department,
            Employee.Status employeeStatus
    ) {
        User user = employee.getUser();
        if (user == null) {
            return;
        }
        user.synchronizeEmployeeDepartment(department);
        user.synchronizeEmployeeStatus(
                employeeStatus == Employee.Status.ACTIVE
                        ? User.Status.ACTIVE
                        : User.Status.INACTIVE
        );
        userRepository.save(user);
    }

    /**
     * Loads a profile by ID or raises the common Employee 404 error.
     */
    private Employee findEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EMPLOYEE_NOT_FOUND_PREFIX + employeeId + "."
                ));
    }

    /**
     * Loads and write-locks a profile for lifecycle or relationship changes.
     */
    private Employee findEmployeeForUpdate(Long employeeId) {
        return employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EMPLOYEE_NOT_FOUND_PREFIX + employeeId + "."
                ));
    }

    /**
     * Loads and write-locks the Department shared with Task assignment and Department deactivation checks.
     */
    private Department findDepartment(Long departmentId) {
        return departmentRepository.findByIdForUpdate(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department not found with id " + departmentId + "."
                ));
    }

    /**
     * Uses the locked Department lookup and rejects inactive destinations for new profiles.
     */
    private Department findActiveDepartment(Long departmentId) {
        Department department = findDepartment(departmentId);
        if (!Boolean.TRUE.equals(department.getActive())) {
            throw new ConflictException("Employees cannot be assigned to an inactive department.");
        }
        return department;
    }

    /**
     * Rejects a duplicate employee-profile email, independently of login-account email.
     */
    private void ensureEmployeeEmailAvailable(String email) {
        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Employee email is already registered.");
        }
    }

    /**
     * Rejects a login email already used by another User.
     */
    private void ensureUserEmailAvailable(String email) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Login email is already registered.");
        }
    }

    /**
     * Requires createLogin and validates the matching set of login fields.
     * Only STAFF or ADMIN can be provisioned here; false forbids login fields rather than creating a placeholder account.
     */
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
                || (request.getSecurityRole() != User.Role.STAFF
                && request.getSecurityRole() != User.Role.ADMIN)) {
            throw new BadRequestException(
                    "A STAFF or ADMIN login email and temporary password are required."
            );
        }
    }

    /**
     * Validates zero-based page, size 1–100, and an allowlisted sort field and direction.
     * Omitted sorting uses name ascending, preventing arbitrary property paths.
     */
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

    /**
     * Maps the profile, nullable User ID, and Department summary into a DTO.
     * Login credentials and token state are not returned.
     */
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

    /**
     * Trims and lowercases profile or login emails with Locale.ROOT before uniqueness checks.
     */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

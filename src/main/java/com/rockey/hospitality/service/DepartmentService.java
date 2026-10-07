package com.rockey.hospitality.service;

import com.rockey.hospitality.entity.Department;
import com.rockey.hospitality.entity.EmployeeStatus;
import com.rockey.hospitality.exception.ConflictException;
import com.rockey.hospitality.exception.ResourceNotFoundException;
import com.rockey.hospitality.repository.DepartmentRepository;
import com.rockey.hospitality.repository.EmployeeRepository;
import com.rockey.hospitality.repository.InventoryItemRepository;
import com.rockey.hospitality.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages Department lifecycle and name uniqueness.
 * Deactivation preserves the row and is blocked while active employees, inventory, or non-terminal tasks reference it.
 */
// Registers this business/security service for constructor injection.
@Service
public class DepartmentService {

    /**
     * Injected DepartmentRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final DepartmentRepository departmentRepository;
    /**
     * Injected EmployeeRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final EmployeeRepository employeeRepository;
    /**
     * Injected TaskRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final TaskRepository taskRepository;
    /**
     * Injected InventoryItemRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final InventoryItemRepository inventoryItemRepository;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public DepartmentService(
            DepartmentRepository departmentRepository,
            EmployeeRepository employeeRepository,
            TaskRepository taskRepository,
            InventoryItemRepository inventoryItemRepository
    ) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.taskRepository = taskRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    /**
     * Returns Departments alphabetically, optionally filtering active or inactive rows.
     * The controller maps these internal entities to response DTOs.
     */
    // Runs this service operation in a read-only transaction, keeping lazy reads and DTO mapping inside the persistence boundary.
    @Transactional(readOnly = true)
    public List<Department> listDepartments(Boolean active) {
        if (active == null) {
            return departmentRepository.findAllByOrderByNameAsc();
        }
        return departmentRepository.findByActiveOrderByNameAsc(active);
    }

    /**
     * Trims the requested name, checks case-insensitive uniqueness, and saves a new active Department.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes.
    @Transactional
    public Department createDepartment(String name, String description) {
        String normalizedName = normalizeName(name);
        ensureNameIsAvailable(normalizedName);

        Department department = new Department(normalizedName, description);
        return departmentRepository.save(department);
    }

    /**
     * Loads one Department or raises the standard missing-resource error.
     */
    // Runs this service operation in a read-only transaction, keeping lazy reads and DTO mapping inside the persistence boundary.
    @Transactional(readOnly = true)
    public Department getDepartment(Long departmentId) {
        return findDepartment(departmentId);
    }

    /**
     * Updates the name and description after checking that another Department does not own the normalized name.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes.
    @Transactional
    public Department updateDepartment(Long departmentId, String name, String description) {
        Department department = findDepartment(departmentId);
        String normalizedName = normalizeName(name);
        ensureNameIsAvailableForUpdate(normalizedName, departmentId);

        department.setName(normalizedName);
        department.setDescription(description);
        return departmentRepository.save(department);
    }

    /**
     * Locks the Department and checks for active Employees, active Inventory, and non-terminal Tasks before setting active=false.
     * The row and related history remain stored.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes.
    @Transactional
    public void deactivateDepartment(Long departmentId) {
        // Preserve history; active Employees/Inventory and non-terminal Tasks block deactivation.
        Department department = departmentRepository.findByIdForUpdate(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department not found with id " + departmentId + "."
                ));
        if (employeeRepository.existsByDepartmentIdAndStatus(
                departmentId,
                EmployeeStatus.ACTIVE
        )) {
            throw new ConflictException(
                    "Department cannot be deactivated while active employees are assigned."
            );
        }
        if (taskRepository.existsByDepartmentIdAndStatusIn(
                departmentId,
                TaskService.NON_TERMINAL_STATUSES
        )) {
            throw new ConflictException(
                    "Department cannot be deactivated while non-terminal tasks exist."
            );
        }
        if (inventoryItemRepository.existsByDepartmentIdAndActiveTrue(departmentId)) {
            throw new ConflictException(
                    "Department cannot be deactivated while active inventory exists."
            );
        }
        department.deactivate();
        departmentRepository.save(department);
    }

    /**
     * Centralizes Department lookup and its 404 error.
     */
    private Department findDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department not found with id " + departmentId + "."
                ));
    }

    /**
     * Rejects a new Department name already present without regard to letter case.
     */
    private void ensureNameIsAvailable(String name) {
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Department name already exists.");
        }
    }

    /**
     * Checks name uniqueness while excluding the Department being edited.
     */
    private void ensureNameIsAvailableForUpdate(String name, Long departmentId) {
        if (departmentRepository.existsByNameIgnoreCaseAndIdNot(name, departmentId)) {
            throw new ConflictException("Department name already exists.");
        }
    }

    /**
     * Trims surrounding whitespace before Department persistence and duplicate checks.
     */
    private String normalizeName(String name) {
        return name.trim();
    }
}

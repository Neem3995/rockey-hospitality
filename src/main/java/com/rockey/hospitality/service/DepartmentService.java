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

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final TaskRepository taskRepository;
    private final InventoryItemRepository inventoryItemRepository;

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

    @Transactional(readOnly = true)
    public List<Department> listDepartments(Boolean active) {
        if (active == null) {
            return departmentRepository.findAllByOrderByNameAsc();
        }
        return departmentRepository.findByActiveOrderByNameAsc(active);
    }

    @Transactional
    public Department createDepartment(String name, String description) {
        String normalizedName = normalizeName(name);
        ensureNameIsAvailable(normalizedName);

        Department department = new Department(normalizedName, description);
        return departmentRepository.save(department);
    }

    @Transactional(readOnly = true)
    public Department getDepartment(Long departmentId) {
        return findDepartment(departmentId);
    }

    @Transactional
    public Department updateDepartment(Long departmentId, String name, String description) {
        Department department = findDepartment(departmentId);
        String normalizedName = normalizeName(name);
        ensureNameIsAvailableForUpdate(normalizedName, departmentId);

        department.setName(normalizedName);
        department.setDescription(description);
        return departmentRepository.save(department);
    }

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

    private Department findDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department not found with id " + departmentId + "."
                ));
    }

    private void ensureNameIsAvailable(String name) {
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Department name already exists.");
        }
    }

    private void ensureNameIsAvailableForUpdate(String name, Long departmentId) {
        if (departmentRepository.existsByNameIgnoreCaseAndIdNot(name, departmentId)) {
            throw new ConflictException("Department name already exists.");
        }
    }

    private String normalizeName(String name) {
        return name.trim();
    }
}

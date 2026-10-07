package com.rockey.hospitality.dto.task;

import jakarta.validation.constraints.Positive;

/**
 * Writable assign task JSON DTO, separate from the entity and returned fields.
 * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
 */
public class AssignTaskRequest {

    /**
     * Optional new assignee ID; null requests unassignment rather than a placeholder Employee.
     */
    // Requires a supplied number to be greater than zero; null is handled separately.
    @Positive(message = "Employee must be positive.")
    private Long employeeId;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }
}

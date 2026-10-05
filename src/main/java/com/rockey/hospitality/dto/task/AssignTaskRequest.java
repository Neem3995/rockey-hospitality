package com.rockey.hospitality.dto.task;

import jakarta.validation.constraints.Positive;

public class AssignTaskRequest {

    @Positive(message = "Employee must be positive.")
    private Long employeeId;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }
}

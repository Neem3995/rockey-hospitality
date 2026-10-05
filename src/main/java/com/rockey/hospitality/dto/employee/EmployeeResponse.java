package com.rockey.hospitality.dto.employee;

import com.rockey.hospitality.dto.auth.DepartmentSummary;
import com.rockey.hospitality.entity.EmployeeStatus;

import java.time.LocalDateTime;

public class EmployeeResponse {

    private final Long id;
    private final Long userId;
    private final String name;
    private final String email;
    private final DepartmentSummary department;
    private final String jobRole;
    private final EmployeeStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public EmployeeResponse(
            Long id,
            Long userId,
            String name,
            String email,
            DepartmentSummary department,
            String jobRole,
            EmployeeStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.department = department;
        this.jobRole = jobRole;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public DepartmentSummary getDepartment() {
        return department;
    }

    public String getJobRole() {
        return jobRole;
    }

    public EmployeeStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

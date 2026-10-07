package com.rockey.hospitality.dto.employee;

import com.rockey.hospitality.dto.auth.DepartmentSummary;
import com.rockey.hospitality.entity.EmployeeStatus;

import java.time.LocalDateTime;

/**
 * Safe employee response DTO built from validated service results instead of serializing the entity.
 */
public class EmployeeResponse {

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    private final Long id;
    /**
     * Nullable login-account ID; null means this profile was created without login access.
     */
    private final Long userId;
    /**
     * Display name used by this resource's request or response, not an authorization role.
     */
    private final String name;
    /**
     * Profile or account email; service-level normalization and uniqueness checks depend on the owning resource.
     */
    private final String email;
    /**
     * Shallow related Department in DTOs, or the owning Department association in entities.
     */
    private final DepartmentSummary department;
    /**
     * Descriptive Employee job title, not a USER/STAFF/ADMIN permission.
     */
    private final String jobRole;
    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    private final EmployeeStatus status;
    /**
     * Server-local creation timestamp retained for history.
     */
    private final LocalDateTime createdAt;
    /**
     * Server-local timestamp of the latest persisted entity update.
     */
    private final LocalDateTime updatedAt;

    /**
     * Packages the listed response fields supplied by the service without serializing a persistence entity.
     */
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

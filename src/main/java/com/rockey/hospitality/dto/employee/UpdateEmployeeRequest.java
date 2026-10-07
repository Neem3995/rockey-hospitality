package com.rockey.hospitality.dto.employee;

import com.rockey.hospitality.entity.EmployeeStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Writable update employee JSON DTO, separate from the entity and returned fields.
 * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
 */
public class UpdateEmployeeRequest {

    /**
     * Display name used by this resource's request or response, not an authorization role.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank(message = "Name is required.")
    // Checks supplied text length from 2 to 100 characters; required text is checked separately.
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters.")
    private String name;

    /**
     * Profile or account email; service-level normalization and uniqueness checks depend on the owning resource.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank(message = "Email is required.")
    // Checks email format when a value is present; required text is enforced separately.
    @Email(message = "Email must be valid.")
    // Checks supplied text length up to 120 characters; required text is checked separately.
    @Size(max = 120, message = "Email must not exceed 120 characters.")
    private String email;

    /**
     * Department identifier used for an explicit relationship or optional query scope.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull(message = "Department is required.")
    // Requires a supplied number to be greater than zero; null is handled separately.
    @Positive(message = "Department must be positive.")
    private Long departmentId;

    /**
     * Descriptive Employee job title, not a USER/STAFF/ADMIN permission.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank(message = "Job role is required.")
    // Checks supplied text length from 2 to 80 characters; required text is checked separately.
    @Size(min = 2, max = 80, message = "Job role must be between 2 and 80 characters.")
    private String jobRole;

    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull(message = "Status is required.")
    private EmployeeStatus status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getJobRole() {
        return jobRole;
    }

    public void setJobRole(String jobRole) {
        this.jobRole = jobRole;
    }

    public EmployeeStatus getStatus() {
        return status;
    }

    public void setStatus(EmployeeStatus status) {
        this.status = status;
    }
}

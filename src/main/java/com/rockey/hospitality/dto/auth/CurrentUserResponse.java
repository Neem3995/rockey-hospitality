package com.rockey.hospitality.dto.auth;

import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.UserStatus;

/**
 * Safe current-account profile separate from the User entity.
 * It includes optional Employee/Department references but no password or token hashes.
 */
public class CurrentUserResponse {

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    private final Long id;
    /**
     * Display name used by this resource's request or response, not an authorization role.
     */
    private final String name;
    /**
     * Profile or account email; service-level normalization and uniqueness checks depend on the owning resource.
     */
    private final String email;
    /**
     * USER/STAFF/ADMIN security role used by backend authorization.
     */
    private final Role role;
    /**
     * Optional shallow authorization-Department summary, not a full JPA entity.
     */
    private final DepartmentSummary departmentSummary;
    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    private final UserStatus status;
    /**
     * Employee identifier used for an explicit relationship or optional query scope.
     */
    private final Long employeeId;

    /**
     * Packages the listed response fields supplied by the service without serializing a persistence entity.
     */
    public CurrentUserResponse(
            Long id,
            String name,
            String email,
            Role role,
            DepartmentSummary departmentSummary,
            UserStatus status,
            Long employeeId
    ) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.departmentSummary = departmentSummary;
        this.status = status;
        this.employeeId = employeeId;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public DepartmentSummary getDepartmentSummary() {
        return departmentSummary;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Long getEmployeeId() {
        return employeeId;
    }
}

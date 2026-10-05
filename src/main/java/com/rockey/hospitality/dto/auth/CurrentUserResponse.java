package com.rockey.hospitality.dto.auth;

import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.UserStatus;

public class CurrentUserResponse {

    private final Long id;
    private final String name;
    private final String email;
    private final Role role;
    private final DepartmentSummary departmentSummary;
    private final UserStatus status;
    private final Long employeeId;

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

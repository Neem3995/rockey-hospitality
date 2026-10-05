package com.rockey.hospitality.dto.employee;

import com.rockey.hospitality.entity.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CreateEmployeeRequest {

    @NotBlank(message = "Name is required.")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters.")
    private String name;

    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be valid.")
    @Size(max = 120, message = "Email must not exceed 120 characters.")
    private String email;

    @NotNull(message = "Department is required.")
    @Positive(message = "Department must be positive.")
    private Long departmentId;

    @NotBlank(message = "Job role is required.")
    @Size(min = 2, max = 80, message = "Job role must be between 2 and 80 characters.")
    private String jobRole;

    @NotNull(message = "createLogin is required.")
    private Boolean createLogin;

    @Email(message = "Login email must be valid.")
    @Size(max = 120, message = "Login email must not exceed 120 characters.")
    private String loginEmail;

    @Size(
            min = 8,
            max = 72,
            message = "Temporary password must be between 8 and 72 characters."
    )
    private String temporaryPassword;

    private Role securityRole;

    @AssertTrue(message = "Login fields must match the createLogin selection.")
    public boolean isLoginConfigurationValid() {
        if (createLogin == null) {
            return true;
        }
        if (!createLogin) {
            return isBlank(loginEmail) && isBlank(temporaryPassword) && securityRole == null;
        }
        return !isBlank(loginEmail)
                && !isBlank(temporaryPassword)
                && (securityRole == Role.STAFF || securityRole == Role.ADMIN);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

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

    public Boolean getCreateLogin() {
        return createLogin;
    }

    public void setCreateLogin(Boolean createLogin) {
        this.createLogin = createLogin;
    }

    public String getLoginEmail() {
        return loginEmail;
    }

    public void setLoginEmail(String loginEmail) {
        this.loginEmail = loginEmail;
    }

    public String getTemporaryPassword() {
        return temporaryPassword;
    }

    public void setTemporaryPassword(String temporaryPassword) {
        this.temporaryPassword = temporaryPassword;
    }

    public Role getSecurityRole() {
        return securityRole;
    }

    public void setSecurityRole(Role securityRole) {
        this.securityRole = securityRole;
    }
}

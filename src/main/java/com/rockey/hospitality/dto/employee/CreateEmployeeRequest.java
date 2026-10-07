package com.rockey.hospitality.dto.employee;

import com.rockey.hospitality.entity.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Writable create employee JSON DTO, separate from the entity and returned fields.
 * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
 */
public class CreateEmployeeRequest {

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
     * Selects whether a new internal STAFF/ADMIN User is created alongside the profile.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull(message = "createLogin is required.")
    private Boolean createLogin;

    /**
     * Optional login email required only when createLogin=true; it may differ from the profile email.
     */
    // Checks email format when a value is present; required text is enforced separately.
    @Email(message = "Login email must be valid.")
    // Checks supplied text length up to 120 characters; required text is checked separately.
    @Size(max = 120, message = "Login email must not exceed 120 characters.")
    private String loginEmail;

    /**
     * Write-only provisioning password, hashed by the service and never returned.
     */
    // Checks supplied text length from 8 to 72 characters; required text is checked separately.
    @Size(
            min = 8,
            max = 72,
            message = "Temporary password must be between 8 and 72 characters."
    )
    private String temporaryPassword;

    /**
     * Provisioned login authorization role restricted to STAFF/ADMIN by cross-field validation, not the descriptive jobRole.
     */
    private Role securityRole;

    /**
     * Checks that login fields match createLogin: no login fields when false, or email, password, and STAFF/ADMIN role when true.
     * Missing createLogin is left to its own null validation.
     */
    // Runs this cross-field predicate as a validation constraint; false rejects the request.
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

    /**
     * Treats null and whitespace-only text alike when checking optional login fields.
     */
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

package com.rockey.hospitality.dto;

import com.rockey.hospitality.dto.AuthDtos.DepartmentSummary;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.User;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * STUDY NOTE: DTO means Data Transfer Object: a plain Java type that carries request, response or internal
 * input data.
 * This container groups Employee creation/update inputs and safe profiles without account secrets.
 * Controllers and services use these types to separate the API contract from JPA entities and avoid
 * exposing database objects directly.
 * Static nested types need no container instance; validation/JSON annotations describe data, not access
 * permissions.
 */
public final class EmployeeDtos {

    // Validation study key (the numbers/patterns are specified on each annotated field):
    // @NotBlank requires non-null text containing at least one non-whitespace character.
    // @NotNull requires a value; it does not check text length or a numeric range.
    // @Size checks length/count against the declared min/max (text length for the fields here).
    // @Email checks email format; required text needs @NotBlank separately.
    // @Positive checks that a supplied number is greater than zero.
    // @AssertTrue requires the annotated check to return true; here it checks conditional login fields.
    // Most shape/range validators accept null; @NotNull or @NotBlank supplies required-value checks.

    // Namespace only; callers construct the nested types instead.
    private EmployeeDtos() { }

    /**
     * Writable create employee JSON DTO, separate from the entity and returned fields.
     * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
     */
    public static class CreateEmployeeRequest {

        /**
         * Display name used by this resource's request or response, not an authorization role.
         */
        @NotBlank(message = "Name is required.")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters.")
        private String name;

        /**
         * Profile or account email; service-level normalization and uniqueness checks depend on the owning resource.
         */
        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be valid.")
        @Size(max = 120, message = "Email must not exceed 120 characters.")
        private String email;

        /**
         * Department identifier used for an explicit relationship or optional query scope.
         */
        @NotNull(message = "Department is required.")
        @Positive(message = "Department must be positive.")
        private Long departmentId;

        /**
         * Descriptive Employee job title, not a USER/STAFF/ADMIN permission.
         */
        @NotBlank(message = "Job role is required.")
        @Size(min = 2, max = 80, message = "Job role must be between 2 and 80 characters.")
        private String jobRole;

        /**
         * Selects whether a new internal STAFF/ADMIN User is created alongside the profile.
         */
        @NotNull(message = "createLogin is required.")
        private Boolean createLogin;

        /**
         * Optional login email required only when createLogin=true; it may differ from the profile email.
         */
        @Email(message = "Login email must be valid.")
        @Size(max = 120, message = "Login email must not exceed 120 characters.")
        private String loginEmail;

        /**
         * Write-only provisioning password, hashed by the service and never returned.
         */
        @Size(
                min = 8,
                max = 72,
                message = "Temporary password must be between 8 and 72 characters."
        )
        private String temporaryPassword;

        /**
         * Provisioned login authorization role restricted to STAFF/ADMIN by cross-field validation, not the descriptive jobRole.
         */
        private User.Role securityRole;

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
                    && (securityRole == User.Role.STAFF || securityRole == User.Role.ADMIN);
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

        public User.Role getSecurityRole() {
            return securityRole;
        }

        public void setSecurityRole(User.Role securityRole) {
            this.securityRole = securityRole;
        }
    }

    /**
     * Safe employee response DTO built from validated service results instead of serializing the entity.
     */
    public static class EmployeeResponse {

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
        private final Employee.Status status;
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
                Employee.Status status,
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

        public Employee.Status getStatus() {
            return status;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }
    }

    /**
     * Writable update employee JSON DTO, separate from the entity and returned fields.
     * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
     */
    public static class UpdateEmployeeRequest {

        /**
         * Display name used by this resource's request or response, not an authorization role.
         */
        @NotBlank(message = "Name is required.")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters.")
        private String name;

        /**
         * Profile or account email; service-level normalization and uniqueness checks depend on the owning resource.
         */
        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be valid.")
        @Size(max = 120, message = "Email must not exceed 120 characters.")
        private String email;

        /**
         * Department identifier used for an explicit relationship or optional query scope.
         */
        @NotNull(message = "Department is required.")
        @Positive(message = "Department must be positive.")
        private Long departmentId;

        /**
         * Descriptive Employee job title, not a USER/STAFF/ADMIN permission.
         */
        @NotBlank(message = "Job role is required.")
        @Size(min = 2, max = 80, message = "Job role must be between 2 and 80 characters.")
        private String jobRole;

        /**
         * Lifecycle enum value interpreted by this resource's service and transition rules.
         */
        @NotNull(message = "Status is required.")
        private Employee.Status status;

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

        public Employee.Status getStatus() {
            return status;
        }

        public void setStatus(Employee.Status status) {
            this.status = status;
        }
    }
}

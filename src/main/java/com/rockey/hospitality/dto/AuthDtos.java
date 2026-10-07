package com.rockey.hospitality.dto;

import com.rockey.hospitality.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * STUDY NOTE: DTO means Data Transfer Object: a plain Java type that carries request, response or internal
 * input data.
 * This container groups login/registration requests, token responses and safe current-account/Department
 * summaries.
 * Controllers and services use these types to separate the API contract from JPA entities and avoid
 * exposing database objects directly.
 * Static nested types need no container instance; validation/JSON annotations describe data, not access
 * permissions.
 */
public final class AuthDtos {

    // Validation study key (the numbers/patterns are specified on each annotated field):
    // @NotBlank requires non-null text containing at least one non-whitespace character.
    // @Size checks length/count against the declared min/max (text length for the fields here).
    // @Email checks email format; required text needs @NotBlank separately.
    // Most shape/range validators accept null; @NotNull or @NotBlank supplies required-value checks.

    // Namespace only; callers construct the nested types instead.
    private AuthDtos() { }

    /**
     * Authentication JSON response containing an access JWT, expiry metadata, and safe current-user details.
     * The raw refresh token is delivered separately as an HttpOnly cookie.
     */
    public static class AuthResponse {

        /**
         * Short-lived access JWT; clients keep it in memory, not browser storage.
         */
        private final String accessToken;
        /**
         * Authorization scheme label used with the returned access JWT.
         */
        private final String tokenType;
        /**
         * Access JWT expiration instant returned as metadata.
         */
        private final Instant accessExpiresAt;
        /**
         * Refresh-session expiration instant returned without the raw refresh token.
         */
        private final Instant refreshExpiresAt;
        /**
         * Safe current-user DTO with no password hash or raw refresh token.
         */
        private final CurrentUserResponse user;

        /**
         * Packages the listed response fields supplied by the service without serializing a persistence entity.
         */
        public AuthResponse(
                String accessToken,
                String tokenType,
                Instant accessExpiresAt,
                Instant refreshExpiresAt,
                CurrentUserResponse user
        ) {
            this.accessToken = accessToken;
            this.tokenType = tokenType;
            this.accessExpiresAt = accessExpiresAt;
            this.refreshExpiresAt = refreshExpiresAt;
            this.user = user;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getTokenType() {
            return tokenType;
        }

        public Instant getAccessExpiresAt() {
            return accessExpiresAt;
        }

        public Instant getRefreshExpiresAt() {
            return refreshExpiresAt;
        }

        public CurrentUserResponse getUser() {
            return user;
        }
    }

    /**
     * Safe current-account profile separate from the User entity.
     * It includes optional Employee/Department references but no password or token hashes.
     */
    public static class CurrentUserResponse {

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
        private final User.Role role;
        /**
         * Optional shallow authorization-Department summary, not a full JPA entity.
         */
        private final DepartmentSummary departmentSummary;
        /**
         * Lifecycle enum value interpreted by this resource's service and transition rules.
         */
        private final User.Status status;
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
                User.Role role,
                DepartmentSummary departmentSummary,
                User.Status status,
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

        public User.Role getRole() {
            return role;
        }

        public DepartmentSummary getDepartmentSummary() {
            return departmentSummary;
        }

        public User.Status getStatus() {
            return status;
        }

        public Long getEmployeeId() {
            return employeeId;
        }
    }

    /**
     * Shallow department response DTO exposing only the listed fields, not a complete JPA relationship graph.
     */
    public static class DepartmentSummary {

        /**
         * Database identifier used to refer to this resource in requests and relationships.
         */
        private final Long id;
        /**
         * Display name used by this resource's request or response, not an authorization role.
         */
        private final String name;

        /**
         * Packages the listed response fields supplied by the service without serializing a persistence entity.
         */
        public DepartmentSummary(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * Writable login JSON DTO, separate from the entity and returned fields.
     * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
     */
    public static class LoginRequest {

        /**
         * Profile or account email; service-level normalization and uniqueness checks depend on the owning resource.
         */
        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be valid.")
        @Size(max = 120, message = "Email must not exceed 120 characters.")
        private String email;

        /**
         * Write-only plaintext credential received for BCrypt matching or hashing; never a response field.
         */
        @NotBlank(message = "Password is required.")
        @Size(max = 72, message = "Password must not exceed 72 characters.")
        private String password;

        /**
         * Allows Jackson to create this request before populating its writable fields from JSON.
         */
        public LoginRequest() {
        }

        /**
         * Creates an explicit request value for callers such as tests; validation still occurs at the request boundary.
         */
        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    /**
     * Writable register user JSON DTO, separate from the entity and returned fields.
     * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
     */
    public static class RegisterUserRequest {

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
         * Write-only plaintext credential received for BCrypt matching or hashing; never a response field.
         */
        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters.")
        private String password;

        /**
         * Allows Jackson to create this request before populating its writable fields from JSON.
         */
        public RegisterUserRequest() {
        }

        /**
         * Creates an explicit request value for callers such as tests; validation still occurs at the request boundary.
         */
        public RegisterUserRequest(String name, String email, String password) {
            this.name = name;
            this.email = email;
            this.password = password;
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

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}

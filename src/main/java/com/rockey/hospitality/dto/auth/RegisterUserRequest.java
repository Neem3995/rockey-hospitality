package com.rockey.hospitality.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Writable register user JSON DTO, separate from the entity and returned fields.
 * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
 */
public class RegisterUserRequest {

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
     * Write-only plaintext credential received for BCrypt matching or hashing; never a response field.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank(message = "Password is required.")
    // Checks supplied text length from 8 to 72 characters; required text is checked separately.
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

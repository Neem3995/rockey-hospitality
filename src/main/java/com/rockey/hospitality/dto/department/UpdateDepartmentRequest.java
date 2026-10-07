package com.rockey.hospitality.dto.department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Writable update department JSON DTO, separate from the entity and returned fields.
 * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
 */
public class UpdateDepartmentRequest {

    /**
     * Display name used by this resource's request or response, not an authorization role.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank(message = "Name is required.")
    // Checks supplied text length from 2 to 100 characters; required text is checked separately.
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters.")
    private String name;

    /**
     * Optional descriptive text; services normalize blank values where required.
     */
    // Checks supplied text length up to 255 characters; required text is checked separately.
    @Size(max = 255, message = "Description must not exceed 255 characters.")
    private String description;

    /**
     * Allows Jackson to create this request before populating its writable fields from JSON.
     */
    public UpdateDepartmentRequest() {
    }

    /**
     * Creates an explicit request value for callers such as tests; validation still occurs at the request boundary.
     */
    public UpdateDepartmentRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

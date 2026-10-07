package com.rockey.hospitality.dto.department;

import java.time.LocalDateTime;

/**
 * Safe department response DTO built from validated service results instead of serializing the entity.
 */
public class DepartmentResponse {

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    private final Long id;
    /**
     * Display name used by this resource's request or response, not an authorization role.
     */
    private final String name;
    /**
     * Optional descriptive text; services normalize blank values where required.
     */
    private final String description;
    /**
     * Soft-lifecycle flag; inactive rows retain their identity and history.
     */
    private final Boolean active;
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
    public DepartmentResponse(
            Long id,
            String name,
            String description,
            Boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Boolean getActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

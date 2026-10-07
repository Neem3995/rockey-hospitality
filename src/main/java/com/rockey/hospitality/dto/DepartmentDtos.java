package com.rockey.hospitality.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * STUDY NOTE: DTO means Data Transfer Object: a plain Java type that carries request, response or internal
 * input data.
 * This container groups Department creation/update inputs and shallow responses.
 * Controllers and services use these types to separate the API contract from JPA entities and avoid
 * exposing database objects directly.
 * Static nested types need no container instance; validation/JSON annotations describe data, not access
 * permissions.
 */
public final class DepartmentDtos {

    // Validation study key (the numbers/patterns are specified on each annotated field):
    // @NotBlank requires non-null text containing at least one non-whitespace character.
    // @Size checks length/count against the declared min/max (text length for the fields here).
    // Most shape/range validators accept null; @NotNull or @NotBlank supplies required-value checks.

    // Namespace only; callers construct the nested types instead.
    private DepartmentDtos() { }

    /**
     * Writable create department JSON DTO, separate from the entity and returned fields.
     * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
     */
    public static class CreateDepartmentRequest {

        /**
         * Display name used by this resource's request or response, not an authorization role.
         */
        @NotBlank(message = "Name is required.")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters.")
        private String name;

        /**
         * Optional descriptive text; services normalize blank values where required.
         */
        @Size(max = 255, message = "Description must not exceed 255 characters.")
        private String description;

        /**
         * Allows Jackson to create this request before populating its writable fields from JSON.
         */
        public CreateDepartmentRequest() {
        }

        /**
         * Creates an explicit request value for callers such as tests; validation still occurs at the request boundary.
         */
        public CreateDepartmentRequest(String name, String description) {
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

    /**
     * Safe department response DTO built from validated service results instead of serializing the entity.
     */
    public static class DepartmentResponse {

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

    /**
     * Writable update department JSON DTO, separate from the entity and returned fields.
     * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
     */
    public static class UpdateDepartmentRequest {

        /**
         * Display name used by this resource's request or response, not an authorization role.
         */
        @NotBlank(message = "Name is required.")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters.")
        private String name;

        /**
         * Optional descriptive text; services normalize blank values where required.
         */
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
}

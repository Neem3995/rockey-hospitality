package com.rockey.hospitality.dto;

import com.rockey.hospitality.dto.AuthDtos.DepartmentSummary;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * STUDY NOTE: DTO means Data Transfer Object: a plain Java type that carries request, response or internal
 * input data.
 * This container groups stock creation/update inputs and responses with Department summaries.
 * Controllers and services use these types to separate the API contract from JPA entities and avoid
 * exposing database objects directly.
 * Static nested types need no container instance; validation/JSON annotations describe data, not access
 * permissions.
 */
public final class InventoryDtos {

    // Validation study key (the numbers/patterns are specified on each annotated field):
    // @NotBlank requires non-null text containing at least one non-whitespace character.
    // @NotNull requires a value; it does not check text length or a numeric range.
    // @Size checks length/count against the declared min/max (text length for the fields here).
    // @Positive checks that a supplied number is greater than zero.
    // @Min sets an inclusive minimum for a supplied number.
    // Most shape/range validators accept null; @NotNull or @NotBlank supplies required-value checks.

    // Namespace only; callers construct the nested types instead.
    private InventoryDtos() { }

    /**
     * Writable create inventory item JSON DTO, separate from the entity and returned fields.
     * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
     */
    public static class CreateInventoryItemRequest {

        /**
         * Display name used by this resource's request or response, not an authorization role.
         */
        @NotBlank(message = "Name is required.")
        @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters.")
        private String name;

        /**
         * Unique normalized stock identifier; updates preserve the item's SKU.
         */
        @NotBlank(message = "SKU is required.")
        private String sku;

        /**
         * Initial absolute on-hand quantity, defaulting to zero when omitted.
         */
        @NotNull(message = "Quantity is required.")
        @Min(value = 0, message = "Quantity must be zero or greater.")
        private Integer quantity = 0;

        /**
         * Initial nonnegative low-stock boundary, defaulting to zero when omitted.
         */
        @NotNull(message = "Reorder threshold is required.")
        @Min(value = 0, message = "Reorder threshold must be zero or greater.")
        private Integer reorderThreshold = 0;

        /**
         * Department identifier used for an explicit relationship or optional query scope.
         */
        @NotNull(message = "Department is required.")
        @Positive(message = "Department ID must be positive.")
        private Long departmentId;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Integer getReorderThreshold() { return reorderThreshold; }
        public void setReorderThreshold(Integer reorderThreshold) { this.reorderThreshold = reorderThreshold; }
        public Long getDepartmentId() { return departmentId; }
        public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    }

    /**
     * Safe inventory item response DTO built from validated service results instead of serializing the entity.
     */
    public static class InventoryItemResponse {

        /**
         * Database identifier used to refer to this resource in requests and relationships.
         */
        private final Long id;
        /**
         * Display name used by this resource's request or response, not an authorization role.
         */
        private final String name;
        /**
         * Unique normalized stock identifier; updates preserve the item's SKU.
         */
        private final String sku;
        /**
         * Absolute on-hand stock units, not an increment or restock delta.
         */
        private final Integer quantity;
        /**
         * Inclusive low-stock boundary: quantity at or below this value triggers the stock condition.
         */
        private final Integer reorderThreshold;
        /**
         * Shallow related Department in DTOs, or the owning Department association in entities.
         */
        private final DepartmentSummary department;
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
        public InventoryItemResponse(Long id, String name, String sku, Integer quantity,
                                     Integer reorderThreshold, DepartmentSummary department,
                                     Boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {
            this.id = id;
            this.name = name;
            this.sku = sku;
            this.quantity = quantity;
            this.reorderThreshold = reorderThreshold;
            this.department = department;
            this.active = active;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public String getSku() { return sku; }
        public Integer getQuantity() { return quantity; }
        public Integer getReorderThreshold() { return reorderThreshold; }
        public DepartmentSummary getDepartment() { return department; }
        public Boolean getActive() { return active; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
    }

    /**
     * Writable update inventory item JSON DTO, separate from the entity and returned fields.
     * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
     */
    public static class UpdateInventoryItemRequest {

        /**
         * Display name used by this resource's request or response, not an authorization role.
         */
        @NotBlank(message = "Name is required.")
        @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters.")
        private String name;

        /**
         * Absolute on-hand stock units, not an increment or restock delta.
         */
        @NotNull(message = "Quantity is required.")
        @Min(value = 0, message = "Quantity must be zero or greater.")
        private Integer quantity;

        /**
         * Inclusive low-stock boundary: quantity at or below this value triggers the stock condition.
         */
        @NotNull(message = "Reorder threshold is required.")
        @Min(value = 0, message = "Reorder threshold must be zero or greater.")
        private Integer reorderThreshold;

        /**
         * Department identifier used for an explicit relationship or optional query scope.
         */
        @NotNull(message = "Department is required.")
        @Positive(message = "Department ID must be positive.")
        private Long departmentId;

        /**
         * Soft-lifecycle flag; inactive rows retain their identity and history.
         */
        @NotNull(message = "Active is required.")
        private Boolean active;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Integer getReorderThreshold() { return reorderThreshold; }
        public void setReorderThreshold(Integer reorderThreshold) { this.reorderThreshold = reorderThreshold; }
        public Long getDepartmentId() { return departmentId; }
        public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }
}

package com.rockey.hospitality.dto.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Writable update inventory item JSON DTO, separate from the entity and returned fields.
 * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
 */
public class UpdateInventoryItemRequest {

    /**
     * Display name used by this resource's request or response, not an authorization role.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank(message = "Name is required.")
    // Checks supplied text length from 2 to 120 characters; required text is checked separately.
    @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters.")
    private String name;

    /**
     * Absolute on-hand stock units, not an increment or restock delta.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull(message = "Quantity is required.")
    // Checks that a supplied number is at least 0.
    @Min(value = 0, message = "Quantity must be zero or greater.")
    private Integer quantity;

    /**
     * Inclusive low-stock boundary: quantity at or below this value triggers the stock condition.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull(message = "Reorder threshold is required.")
    // Checks that a supplied number is at least 0.
    @Min(value = 0, message = "Reorder threshold must be zero or greater.")
    private Integer reorderThreshold;

    /**
     * Department identifier used for an explicit relationship or optional query scope.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull(message = "Department is required.")
    // Requires a supplied number to be greater than zero; null is handled separately.
    @Positive(message = "Department ID must be positive.")
    private Long departmentId;

    /**
     * Soft-lifecycle flag; inactive rows retain their identity and history.
     */
    // Requires a value; further shape or range checks are separate.
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

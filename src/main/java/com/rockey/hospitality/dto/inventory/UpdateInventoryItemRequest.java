package com.rockey.hospitality.dto.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class UpdateInventoryItemRequest {

    @NotBlank(message = "Name is required.")
    @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters.")
    private String name;

    @NotNull(message = "Quantity is required.")
    @Min(value = 0, message = "Quantity must be zero or greater.")
    private Integer quantity;

    @NotNull(message = "Reorder threshold is required.")
    @Min(value = 0, message = "Reorder threshold must be zero or greater.")
    private Integer reorderThreshold;

    @NotNull(message = "Department is required.")
    @Positive(message = "Department ID must be positive.")
    private Long departmentId;

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

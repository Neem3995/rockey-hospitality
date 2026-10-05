package com.rockey.hospitality.dto.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CreateInventoryItemRequest {

    @NotBlank(message = "Name is required.")
    @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters.")
    private String name;

    @NotBlank(message = "SKU is required.")
    private String sku;

    @NotNull(message = "Quantity is required.")
    @Min(value = 0, message = "Quantity must be zero or greater.")
    private Integer quantity = 0;

    @NotNull(message = "Reorder threshold is required.")
    @Min(value = 0, message = "Reorder threshold must be zero or greater.")
    private Integer reorderThreshold = 0;

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

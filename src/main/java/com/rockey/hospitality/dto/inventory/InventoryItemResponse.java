package com.rockey.hospitality.dto.inventory;

import com.rockey.hospitality.dto.auth.DepartmentSummary;

import java.time.LocalDateTime;

public class InventoryItemResponse {

    private final Long id;
    private final String name;
    private final String sku;
    private final Integer quantity;
    private final Integer reorderThreshold;
    private final DepartmentSummary department;
    private final Boolean active;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

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

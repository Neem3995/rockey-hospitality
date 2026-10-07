package com.rockey.hospitality.dto.inventory;

import com.rockey.hospitality.dto.auth.DepartmentSummary;

import java.time.LocalDateTime;

/**
 * Safe inventory item response DTO built from validated service results instead of serializing the entity.
 */
public class InventoryItemResponse {

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

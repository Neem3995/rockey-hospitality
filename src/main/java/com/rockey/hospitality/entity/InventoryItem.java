package com.rockey.hospitality.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Persists stock identity, absolute quantity, threshold, and Department.
 * The active flag removes it from operational use without deleting history.
 */
// Marks a JPA-mapped database entity; the configured application validates the supplied schema instead of creating it.
@Entity
// Maps to the existing inventory_items table; @Index describes existing lookup indexes and @UniqueConstraint describes unique keys. These mappings do not create the application schema.
@Table(
        name = "inventory_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_items_sku", columnNames = "sku"),
        indexes = {
                @Index(name = "idx_inventory_department_active", columnList = "department_id,active"),
                @Index(name = "idx_inventory_quantity_threshold", columnList = "quantity,reorder_threshold")
        }
)
public class InventoryItem {

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    // Identifies the entity's primary-key field.
    @Id
    // Uses the database IDENTITY mechanism to generate the primary key.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Requires a supplied number to be greater than zero; null is handled separately.
    @Positive
    private Long id;

    /**
     * Stored display name for this record; it grants no permissions.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank
    // Checks supplied text length from 2 to 120 characters; required text is checked separately.
    @Size(min = 2, max = 120)
    // Maps this field to its matching SQL column (non-null, length 120) in the supplied schema.
    @Column(nullable = false, length = 120)
    private String name;

    /**
     * Unique normalized stock identifier; updates preserve the item's SKU.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank
    // Checks this supplied value against the fixed regular expression for its canonical format; it does not build patterns from request input.
    @Pattern(regexp = "[A-Z0-9-]{1,40}")
    // Maps this field to its matching SQL column (non-null, length 40, not rewritten by JPA updates) in the supplied schema.
    @Column(nullable = false, length = 40, updatable = false)
    private String sku;

    /**
     * Absolute on-hand stock units, not an increment or restock delta.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull
    // Checks that a supplied number is at least 0.
    @Min(0)
    // Maps this field to its matching SQL column (non-null) in the supplied schema.
    @Column(nullable = false)
    private Integer quantity = 0;

    /**
     * Inclusive low-stock boundary: quantity at or below this value triggers the stock condition.
     */
    // Requires a value; further shape or range checks are separate.
    @NotNull
    // Checks that a supplied number is at least 0.
    @Min(0)
    // Maps this field to reorder_threshold SQL column (non-null) in the supplied schema.
    @Column(name = "reorder_threshold", nullable = false)
    private Integer reorderThreshold = 0;

    /**
     * Required owning Department used for stock-read scope and deactivation guards.
     */
    // Many rows can reference the same related entity; LAZY loads the relationship when it is needed within the service transaction.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    // The Department locates stock; services enforce which STAFF can view that stock.
    // Stores this relationship's foreign key in department_id, which must be present.
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    /**
     * Soft-lifecycle flag; inactive rows retain their identity and history.
     */
    // Maps this field to its matching SQL column (non-null) in the supplied schema.
    @Column(nullable = false)
    private Boolean active = true;

    /**
     * Server-local creation timestamp retained for history.
     */
    // Maps this field to created_at SQL column (non-null, not rewritten by JPA updates) in the supplied schema.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Server-local timestamp of the latest persisted entity update.
     */
    // Maps this field to updated_at SQL column (non-null) in the supplied schema.
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Required no-argument constructor for Hibernate to instantiate persisted entities; application creation uses the explicit constructor.
     */
    protected InventoryItem() {
    }

    /**
     * Builds InventoryItem from the values checked by its service, retaining the entity's initial lifecycle defaults.
     */
    public InventoryItem(String name, String sku, Integer quantity,
                         Integer reorderThreshold, Department department) {
        this.name = name;
        this.sku = sku;
        this.quantity = quantity;
        this.reorderThreshold = reorderThreshold;
        this.department = department;
    }

    /**
     * Initializes creation and update timestamps from server-local time before the first database insert.
     */
    // Runs this lifecycle callback before the entity's first insert.
    @PrePersist
    void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Refreshes updatedAt using server-local time before Hibernate writes an entity update.
     */
    // Runs this lifecycle callback before a changed entity is written.
    @PreUpdate
    void prepareForUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Replaces absolute stock quantities, threshold, Department, and active state after service checks.
     * SKU identity is unchanged.
     */
    public void updateDetails(String name, Integer quantity, Integer reorderThreshold,
                              Department department, Boolean active) {
        this.name = name;
        this.quantity = quantity;
        this.reorderThreshold = reorderThreshold;
        this.department = department;
        this.active = active;
    }

    /**
     * Marks stock inactive while retaining its quantities and Department reference.
     */
    public void deactivate() {
        active = false;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSku() { return sku; }
    public Integer getQuantity() { return quantity; }
    public Integer getReorderThreshold() { return reorderThreshold; }
    public Department getDepartment() { return department; }
    public Boolean getActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

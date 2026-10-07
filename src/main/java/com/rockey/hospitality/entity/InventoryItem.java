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
 * STUDY NOTE: An Entity is a Java class JPA/Hibernate maps to stored database rows.
 * Here, @Entity marks this persistent class, while @Table selects the inventory_items MySQL table.
 * InventoryItem stores quantity, threshold and a required Department for InventoryService and alert
 * automation.
 * schema.sql creates the tables; Hibernate validates their shape instead of creating them.
 */
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

    // Persistence study key:
    // @UniqueConstraint describes a unique key so duplicate field values or join pairs cannot be stored.
    // @JoinColumn names a foreign-key column linking this row to another table's primary key.
    // @Id marks the primary key; @GeneratedValue(IDENTITY) lets MySQL generate it on insertion.
    // @Column maps a Java field to a SQL column; nullable/length settings describe the supplied schema.
    // @PrePersist runs before the first insert; the callback supplies lifecycle defaults and timestamps.
    // @PreUpdate runs before an entity update; the callback refreshes its update timestamp.
    // @Index describes an existing lookup index; schema.sql, not these comments or mappings, creates it.
    // Time study key: callbacks use server-local LocalDateTime; Hibernate's JDBC time-zone setting is UTC.
    // MySQL DATETIME has no zone label; do not silently treat every operational API LocalDateTime as UTC.
    // Validation study key (the numbers/patterns are specified on each annotated field):
    // @NotBlank requires non-null text containing at least one non-whitespace character.
    // @NotNull requires a value; it does not check text length or a numeric range.
    // @Size checks length/count against the declared min/max (text length for the fields here).
    // @Positive checks that a supplied number is greater than zero.
    // @Min sets an inclusive minimum for a supplied number.
    // @Pattern checks supplied text against the fixed regular expression written on the field.
    // Most shape/range validators accept null; @NotNull or @NotBlank supplies required-value checks.

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Positive
    private Long id;

    /**
     * Stored display name for this record; it grants no permissions.
     */
    @NotBlank
    @Size(min = 2, max = 120)
    // Maps this field to its matching SQL column (non-null, length 120) in the supplied schema.
    @Column(nullable = false, length = 120)
    private String name;

    /**
     * Unique normalized stock identifier; updates preserve the item's SKU.
     */
    @NotBlank
    // Checks this supplied value against the fixed regular expression for its canonical format; it does not build patterns from request input.
    @Pattern(regexp = "[A-Z0-9-]{1,40}")
    // Maps this field to its matching SQL column (non-null, length 40, not rewritten by JPA updates) in the supplied schema.
    @Column(nullable = false, length = 40, updatable = false)
    private String sku;

    /**
     * Absolute on-hand stock units, not an increment or restock delta.
     */
    @NotNull
    @Min(0)
    // Maps this field to its matching SQL column (non-null) in the supplied schema.
    @Column(nullable = false)
    private Integer quantity = 0;

    /**
     * Inclusive low-stock boundary: quantity at or below this value triggers the stock condition.
     */
    @NotNull
    @Min(0)
    // Maps this field to reorder_threshold SQL column (non-null) in the supplied schema.
    @Column(name = "reorder_threshold", nullable = false)
    private Integer reorderThreshold = 0;

    /**
     * Required owning Department used for stock-read scope and deactivation guards.
     */
    // @ManyToOne lets many InventoryItem rows reference the same Department; LAZY defers loading it until needed.
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
    @PrePersist
    void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Refreshes updatedAt using server-local time before Hibernate writes an entity update.
     */
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

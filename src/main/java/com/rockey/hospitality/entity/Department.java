package com.rockey.hospitality.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Persists one operational Department and its active flag.
 * Soft deactivation keeps related employee, work, and inventory history.
 */
// Marks a JPA-mapped database entity; the configured application validates the supplied schema instead of creating it.
@Entity
// Maps to the existing departments table; @UniqueConstraint describes unique keys. These mappings do not create the application schema.
@Table(
        name = "departments",
        uniqueConstraints = @UniqueConstraint(name = "uk_departments_name", columnNames = "name")
)
public class Department {
    // Employees, Tasks and Inventory reference this row; deactivation must preserve their history.

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
    // Checks supplied text length from 2 to 100 characters; required text is checked separately.
    @Size(min = 2, max = 100)
    // Maps this field to its matching SQL column (non-null, length 100) in the supplied schema.
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Optional descriptive text; services normalize blank values where required.
     */
    // Checks supplied text length up to 255 characters; required text is checked separately.
    @Size(max = 255)
    // Maps this field to its matching SQL column (length 255) in the supplied schema.
    @Column(length = 255)
    private String description;

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
    protected Department() {
    }

    /**
     * Builds Department from the values checked by its service, retaining the entity's initial lifecycle defaults.
     */
    public Department(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * Defaults a missing active flag and initializes creation/update timestamps from server-local time.
     */
    // Runs this lifecycle callback before the entity's first insert.
    @PrePersist
    void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (active == null) {
            active = true;
        }
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

    public Long getId() {
        return id;
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

    public Boolean getActive() {
        return active;
    }

    /**
     * Marks the Department inactive without deleting it; DepartmentService checks active references first.
     */
    public void deactivate() {
        active = false;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

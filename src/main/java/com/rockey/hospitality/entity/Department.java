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
 * STUDY NOTE: An Entity is a Java class JPA/Hibernate maps to stored database rows.
 * Here, @Entity marks this persistent class, while @Table selects the departments MySQL table.
 * Department stores work-group identity and active state; services check its Employee, Task and Inventory
 * references before deactivation.
 * schema.sql creates the tables; Hibernate validates their shape instead of creating them.
 */
@Entity
// Maps to the existing departments table; @UniqueConstraint describes unique keys. These mappings do not create the application schema.
@Table(
        name = "departments",
        uniqueConstraints = @UniqueConstraint(name = "uk_departments_name", columnNames = "name")
)
public class Department {

    // Persistence study key:
    // @UniqueConstraint describes a unique key so duplicate field values or join pairs cannot be stored.
    // @Id marks the primary key; @GeneratedValue(IDENTITY) lets MySQL generate it on insertion.
    // @Column maps a Java field to a SQL column; nullable/length settings describe the supplied schema.
    // @PrePersist runs before the first insert; the callback supplies lifecycle defaults and timestamps.
    // @PreUpdate runs before an entity update; the callback refreshes its update timestamp.
    // Time study key: callbacks use server-local LocalDateTime; Hibernate's JDBC time-zone setting is UTC.
    // MySQL DATETIME has no zone label; do not silently treat every operational API LocalDateTime as UTC.
    // Validation study key (the numbers/patterns are specified on each annotated field):
    // @NotBlank requires non-null text containing at least one non-whitespace character.
    // @Size checks length/count against the declared min/max (text length for the fields here).
    // @Positive checks that a supplied number is greater than zero.
    // Most shape/range validators accept null; @NotNull or @NotBlank supplies required-value checks.
    // Employees, Tasks and Inventory reference this row; deactivation must preserve their history.

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
    @Size(min = 2, max = 100)
    // Maps this field to its matching SQL column (non-null, length 100) in the supplied schema.
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Optional descriptive text; services normalize blank values where required.
     */
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

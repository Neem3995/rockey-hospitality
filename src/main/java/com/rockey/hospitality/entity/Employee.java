package com.rockey.hospitality.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Persists an employee's work profile in a required Department with an optional unique User login link.
 * Services keep a linked User's Department and status consistent.
 */
// Marks a JPA-mapped database entity; the configured application validates the supplied schema instead of creating it.
@Entity
// Maps to the existing employees table; @UniqueConstraint describes unique keys. These mappings do not create the application schema.
@Table(
        name = "employees",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_employees_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_employees_email", columnNames = "email")
        }
)
public class Employee {

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
     * Optional unique login link; a profile without a User has no application login access.
     */
    // Optional one-to-one login relationship; LAZY avoids loading account details until needed, and the join column is unique when present.
    @OneToOne(fetch = FetchType.LAZY, optional = true)
    // Employment may exist without login; a non-null User link is unique across profiles.
    // Stores this relationship's foreign key in user_id, which may be null; non-null links must be unique.
    @JoinColumn(name = "user_id", unique = true)
    private User user;

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
     * Profile or account email; service-level normalization and uniqueness checks depend on the owning resource.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank
    // Checks email format when a value is present; required text is enforced separately.
    @Email
    // Checks supplied text length up to 120 characters; required text is checked separately.
    @Size(max = 120)
    // Maps this field to its matching SQL column (non-null, length 120) in the supplied schema.
    @Column(nullable = false, length = 120)
    private String email;

    /**
     * Required work Department shared by many employees; it is not itself a security role.
     */
    // Many rows can reference the same related entity; LAZY loads the relationship when it is needed within the service transaction.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    // Stores this relationship's foreign key in department_id, which must be present.
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    /**
     * Descriptive Employee job title, not a USER/STAFF/ADMIN permission.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank
    // Checks supplied text length from 2 to 80 characters; required text is checked separately.
    @Size(min = 2, max = 80)
    // Maps this field to job_role SQL column (non-null, length 80) in the supplied schema.
    @Column(name = "job_role", nullable = false, length = 80)
    private String jobRole;

    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    // Stores the enum's name as text, not its numeric ordinal.
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 20) in the supplied schema.
    @Column(nullable = false, length = 20)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

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
    protected Employee() {
    }

    /**
     * Builds Employee from the values checked by its service, retaining the entity's initial lifecycle defaults.
     */
    public Employee(
            String name,
            String email,
            Department department,
            String jobRole,
            User user
    ) {
        this.name = name;
        this.email = email;
        this.department = department;
        this.jobRole = jobRole;
        this.user = user;
    }

    /**
     * Defaults a missing status to ACTIVE and initializes creation/update timestamps from server-local time.
     */
    // Runs this lifecycle callback before the entity's first insert.
    @PrePersist
    void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = EmployeeStatus.ACTIVE;
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

    /**
     * Replaces profile state after service validation.
     * Synchronizing a linked User is EmployeeService's responsibility.
     */
    public void updateProfile(
            String name,
            String email,
            Department department,
            String jobRole,
            EmployeeStatus status
    ) {
        this.name = name;
        this.email = email;
        this.department = department;
        this.jobRole = jobRole;
        this.status = status;
    }

    /**
     * Sets the profile INACTIVE without removing its User link or work history.
     */
    public void deactivate() {
        status = EmployeeStatus.INACTIVE;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Department getDepartment() {
        return department;
    }

    public String getJobRole() {
        return jobRole;
    }

    public EmployeeStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

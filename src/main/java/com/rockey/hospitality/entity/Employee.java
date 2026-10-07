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
 * STUDY NOTE: An Entity is a Java class JPA/Hibernate maps to stored database rows.
 * Here, @Entity marks this persistent class, while @Table selects the employees MySQL table.
 * Employee links a worker to a required Department and an optional unique User login; EmployeeService keeps
 * linked records consistent.
 * schema.sql creates the tables; Hibernate validates their shape instead of creating them.
 */
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

    // Persistence study key:
    // @JoinColumn names a foreign-key column linking this row to another table's primary key.
    // @Id marks the primary key; @GeneratedValue(IDENTITY) lets MySQL generate it on insertion.
    // @Column maps a Java field to a SQL column; nullable/length settings describe the supplied schema.
    // @Enumerated(STRING) stores enum names such as ACTIVE, not positions such as 0 or 1.
    // @PrePersist runs before the first insert; the callback supplies lifecycle defaults and timestamps.
    // @PreUpdate runs before an entity update; the callback refreshes its update timestamp.
    // @UniqueConstraint describes a unique key so duplicate field values or join pairs cannot be stored.
    // Time study key: callbacks use server-local LocalDateTime; Hibernate's JDBC time-zone setting is UTC.
    // MySQL DATETIME has no zone label; do not silently treat every operational API LocalDateTime as UTC.
    // Validation study key (the numbers/patterns are specified on each annotated field):
    // @NotBlank requires non-null text containing at least one non-whitespace character.
    // @Size checks length/count against the declared min/max (text length for the fields here).
    // @Email checks email format; required text needs @NotBlank separately.
    // @Positive checks that a supplied number is greater than zero.
    // Most shape/range validators accept null; @NotNull or @NotBlank supplies required-value checks.

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Positive
    private Long id;

    /**
     * Optional unique login link; a profile without a User has no application login access.
     */
    // @OneToOne permits at most one User per Employee; the unique join permits at most one Employee per User.
    @OneToOne(fetch = FetchType.LAZY, optional = true)
    // Employment may exist without login; a non-null User link is unique across profiles.
    // Stores this relationship's foreign key in user_id, which may be null; non-null links must be unique.
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    /**
     * Stored display name for this record; it grants no permissions.
     */
    @NotBlank
    @Size(min = 2, max = 100)
    // Maps this field to its matching SQL column (non-null, length 100) in the supplied schema.
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Profile or account email; service-level normalization and uniqueness checks depend on the owning resource.
     */
    @NotBlank
    @Email
    @Size(max = 120)
    // Maps this field to its matching SQL column (non-null, length 120) in the supplied schema.
    @Column(nullable = false, length = 120)
    private String email;

    /**
     * Required work Department shared by many employees; it is not itself a security role.
     */
    // @ManyToOne lets many Employee rows reference the same Department; LAZY defers loading it until needed.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    // Stores this relationship's foreign key in department_id, which must be present.
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    /**
     * Descriptive Employee job title, not a USER/STAFF/ADMIN permission.
     */
    @NotBlank
    @Size(min = 2, max = 80)
    // Maps this field to job_role SQL column (non-null, length 80) in the supplied schema.
    @Column(name = "job_role", nullable = false, length = 80)
    private String jobRole;

    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 20) in the supplied schema.
    @Column(nullable = false, length = 20)
    private Employee.Status status = Employee.Status.ACTIVE;

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
    @PrePersist
    void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = Employee.Status.ACTIVE;
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

    /**
     * Replaces profile state after service validation.
     * Synchronizing a linked User is EmployeeService's responsibility.
     */
    public void updateProfile(
            String name,
            String email,
            Department department,
            String jobRole,
            Employee.Status status
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
        status = Employee.Status.INACTIVE;
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

    public Employee.Status getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }


    /**
     * Employee.Status is an enum: a Java type limited to a fixed set of valid choices.
     * Controls whether an employee profile is ACTIVE or INACTIVE.
     * Linked User eligibility is synchronized by EmployeeService.
     */
    public enum Status {
        /**
         * Eligible active work profile.
         */
        ACTIVE,
        /**
         * Retained profile disabled for new operational assignments.
         */
        INACTIVE
    }
}

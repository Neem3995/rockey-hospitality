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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * STUDY NOTE: An Entity is a Java class JPA/Hibernate maps to stored database rows.
 * Here, @Entity marks this persistent class, while @Table selects the users MySQL table.
 * User stores login identity, BCrypt password hash, security role and one refresh-session hash/expiry for
 * authentication services.
 * schema.sql creates the tables; Hibernate validates their shape instead of creating them.
 */
@Entity
// Maps to the existing users table; @UniqueConstraint describes unique keys. These mappings do not create the application schema.
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(
                        name = "uk_users_refresh_token_hash",
                        columnNames = "refresh_token_hash"
                )
        }
)
public class User {

    // Persistence study key:
    // @JoinTable selects the association table; its two join columns link Users with Events.
    // @JoinColumn names a foreign-key column linking this row to another table's primary key.
    // @Id marks the primary key; @GeneratedValue(IDENTITY) lets MySQL generate it on insertion.
    // @Column maps a Java field to a SQL column; nullable/length settings describe the supplied schema.
    // @Enumerated(STRING) stores enum names such as USER, not positions such as 0 or 1.
    // @PrePersist runs before the first insert; the callback supplies lifecycle defaults and timestamps.
    // @PreUpdate runs before an entity update; the callback refreshes its update timestamp.
    // @UniqueConstraint describes a unique key so duplicate field values or join pairs cannot be stored.
    // Time study key: callbacks use server-local LocalDateTime; Hibernate's JDBC time-zone setting is UTC.
    // MySQL DATETIME has no zone label; do not silently treat every operational API LocalDateTime as UTC.
    // Refresh-session expiry is explicitly UTC; BCrypt hashes passwords and cannot be decrypted to recover
    // them.
    // User.Role grants permissions; Employee.jobRole and Department describe work context, not roles.
    // Validation study key (the numbers/patterns are specified on each annotated field):
    // @NotBlank requires non-null text containing at least one non-whitespace character.
    // @Size checks length/count against the declared min/max (text length for the fields here).
    // @Email checks email format; required text needs @NotBlank separately.
    // @Positive checks that a supplied number is greater than zero.
    // @PositiveOrZero allows zero but rejects a negative supplied number.
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
     * BCrypt password hash used only for matching credentials; no response DTO exposes it.
     */
    @NotBlank
    @Size(max = 255)
    // Maps this field to password_hash SQL column (non-null, length 255) in the supplied schema.
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * USER/STAFF/ADMIN security role used by backend authorization.
     */
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 20) in the supplied schema.
    @Column(nullable = false, length = 20)
    private User.Role role = User.Role.USER;

    /**
     * Optional authorization Department; EmployeeService keeps it equal to a linked Employee's Department.
     */
    // @ManyToOne lets many User rows reference the same Department; LAZY defers loading it until needed.
    @ManyToOne(fetch = FetchType.LAZY)
    // Stores this relationship's foreign key in department_id, which may be null.
    @JoinColumn(name = "department_id")
    private Department department;

    /**
     * Lifecycle enum value interpreted by this resource's service and transition rules.
     */
    @Enumerated(EnumType.STRING)
    // Maps this field to its matching SQL column (non-null, length 20) in the supplied schema.
    @Column(nullable = false, length = 20)
    private User.Status status = User.Status.ACTIVE;

    /**
     * User-owned Event memberships persisted through event_registrations, not a separate registration entity.
     */
    // @ManyToMany means many Users can join many Events; LAZY defers reading the membership collection.
    @ManyToMany(fetch = FetchType.LAZY)
    // Each membership is a User/Event pair in event_registrations, not a separate account type.
    // Uses event_registrations for User/Event membership: nested @JoinColumn maps both foreign keys and @UniqueConstraint prevents duplicate pairs.
    @JoinTable(
            name = "event_registrations",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "event_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_event_registrations_user_event",
                    columnNames = {"user_id", "event_id"}
            )
    )
    private Set<Event> registeredEvents = new HashSet<>();

    /**
     * Retained legacy nonnegative version field; the current opaque refresh flow uses hash replacement rather than this value.
     */
    // Requires a supplied number to be nonnegative.
    @PositiveOrZero
    // Maps this field to token_version SQL column (non-null) in the supplied schema.
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    /**
     * SHA-256 hexadecimal hash of the single active refresh token; never the raw cookie value.
     */
    @Size(max = 64)
    // Maps this field to refresh_token_hash SQL column (length 64) in the supplied schema.
    @Column(name = "refresh_token_hash", length = 64)
    private String refreshTokenHash;

    /**
     * UTC LocalDateTime expiry for the stored refresh session; null means there is no active refresh state.
     */
    // Maps this field to refresh_token_expires_at SQL column in the supplied schema.
    @Column(name = "refresh_token_expires_at")
    private LocalDateTime refreshTokenExpiresAt;

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
    protected User() {
    }

    /**
     * Builds an account from normalized identity and an already encoded password; default USER role and ACTIVE status remain in place.
     */
    public User(String name, String email, String passwordHash) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    /**
     * Defaults USER role, ACTIVE status, and the legacy token version and initializes creation/update timestamps from server-local time.
     */
    @PrePersist
    void prepareForInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (role == null) {
            role = User.Role.USER;
        }
        if (status == null) {
            status = User.Status.ACTIVE;
        }
        if (tokenVersion == null) {
            tokenVersion = 0;
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
     * Replaces the single persisted refresh hash and UTC expiry, invalidating the previous refresh value.
     */
    public void replaceRefreshSession(String refreshTokenHash, LocalDateTime expiresAt) {
        this.refreshTokenHash = refreshTokenHash;
        this.refreshTokenExpiresAt = expiresAt;
    }

    /**
     * Clears both refresh fields safely, even when already null.
     * Existing access JWTs are not revoked by this operation alone.
     */
    public void clearRefreshSession() {
        refreshTokenHash = null;
        refreshTokenExpiresAt = null;
    }

    /**
     * Sets the internal login role, Department, and active status after EmployeeService restricts provisioning to STAFF/ADMIN.
     */
    public void provisionEmployeeAccess(User.Role role, Department department) {
        this.role = role;
        this.department = department;
        this.status = User.Status.ACTIVE;
    }

    /**
     * Keeps the linked login's authorization Department equal to its Employee's work Department.
     */
    public void synchronizeEmployeeDepartment(Department department) {
        this.department = department;
    }

    /**
     * Matches linked account status to Employee lifecycle and revokes refresh state when the account becomes inactive.
     */
    public void synchronizeEmployeeStatus(User.Status status) {
        this.status = status;
        if (status == User.Status.INACTIVE) {
            clearRefreshSession();
        }
    }

    /**
     * Adds an Event membership to the owning User collection after RegistrationService checks eligibility, duplication, and capacity.
     */
    public void registerForEvent(Event event) {
        registeredEvents.add(event);
    }

    /**
     * Removes only this User's Event membership, leaving the Event and other registrations intact.
     */
    public void withdrawFromEvent(Event event) {
        registeredEvents.remove(event);
    }

    public Set<Event> getRegisteredEvents() {
        return registeredEvents;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public User.Role getRole() {
        return role;
    }

    public Department getDepartment() {
        return department;
    }

    public User.Status getStatus() {
        return status;
    }

    public Integer getTokenVersion() {
        return tokenVersion;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public LocalDateTime getRefreshTokenExpiresAt() {
        return refreshTokenExpiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }


    /**
     * User.Role is an enum: a Java type limited to a fixed set of valid choices.
     * Defines backend security roles: USER for attendee actions, STAFF for authorized operational scope, and ADMIN for oversight.
     * These are not Employee job titles or Department names.
     */
    public enum Role {
        /**
         * Attendee account permissions, including own Event registration.
         */
        USER,
        /**
         * Operational permissions restricted by eligible identity and Department scope.
         */
        STAFF,
        /**
         * Operational oversight and management permissions.
         */
        ADMIN
    }

    /**
     * User.Status is an enum: a Java type limited to a fixed set of valid choices.
     * Controls whether an account is ACTIVE or INACTIVE.
     * JWT authentication reloads this state, and Employee deactivation also clears linked refresh state.
     */
    public enum Status {
        /**
         * Account eligible for authentication.
         */
        ACTIVE,
        /**
         * Retained account that cannot authenticate.
         */
        INACTIVE
    }
}

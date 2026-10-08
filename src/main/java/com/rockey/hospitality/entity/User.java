package com.rockey.hospitality.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * STUDY NOTE: An entity is a Java object mapped to a stored database row; this one maps the users table.
 * @Entity/@Table select the table, @Id/@GeneratedValue use the MySQL-generated key, @Column describes fields,
 * and @Enumerated(STRING) stores role names such as USER rather than numeric positions.
 * User is both the login identity and the housekeeping worker; role USER/MANAGER/ADMIN is the only permission.
 * It also holds the refresh-session hash. @PrePersist/@PreUpdate stamp server-local timestamps.
 * schema.sql creates the table and Hibernate only validates that this mapping matches it.
 */
@Entity
@Table(name = "users")
public class User {
    public enum Role { USER, MANAGER, ADMIN }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, unique = true, length = 120)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Role role = Role.USER;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "refresh_token_hash", unique = true, length = 64)
    private String refreshTokenHash;
    @Column(name = "refresh_token_expires_at")
    private LocalDateTime refreshTokenExpiresAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Hibernate uses the no-argument constructor to load a stored account. */
    protected User() { }
    /** Public account creation defaults to USER; passwords are already BCrypt hashed. */
    public User(String name, String email, String passwordHash) {
        this.name = name; this.email = email; this.passwordHash = passwordHash;
    }
    /** Explicit internal provisioning selects USER or MANAGER after service permission checks. */
    public User(String name, String email, String passwordHash, Role role) {
        this(name, email, passwordHash); this.role = role;
    }
    @PrePersist
    void prepareForInsert() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate
    void prepareForUpdate() { updatedAt = LocalDateTime.now(); }
    /** Changes safe account details; disabling or changing permissions revokes refresh state. */
    public void update(String name, String email, Role role, boolean active) {
        if (!this.email.equals(email) || this.role != role || this.active != active) clearRefreshSession();
        this.name = name; this.email = email; this.role = role; this.active = active;
    }
    /** Keeps identity/history while preventing future authentication. */
    public void deactivate() { active = false; clearRefreshSession(); }
    /** Rotation retains only a hash, never the raw refresh cookie. */
    public void replaceRefreshSession(String hash, LocalDateTime expiration) {
        refreshTokenHash = hash; refreshTokenExpiresAt = expiration;
    }
    /** Idempotently revokes refresh capability; short-lived access JWTs are not blacklisted. */
    public void clearRefreshSession() { refreshTokenHash = null; refreshTokenExpiresAt = null; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public LocalDateTime getRefreshTokenExpiresAt() { return refreshTokenExpiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

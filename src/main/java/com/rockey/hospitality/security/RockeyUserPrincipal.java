package com.rockey.hospitality.security;

import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.entity.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapts a database User to Spring Security's authenticated identity.
 * Its internal password hash is for authentication, not a response field.
 */
public class RockeyUserPrincipal implements UserDetails {

    /**
     * Database identifier used to refer to this resource in requests and relationships.
     */
    private final Long id;
    /**
     * Database login email returned by the UserDetails username contract.
     */
    private final String email;
    /**
     * Internal BCrypt hash required by UserDetails, not an API response value.
     */
    private final String passwordHash;
    /**
     * USER/STAFF/ADMIN security role used by backend authorization.
     */
    private final Role role;
    /**
     * Current database account eligibility snapshot used by JWT authentication.
     */
    private final boolean active;

    /**
     * Snapshots the database User's identity, role, password hash, and active status for the security context.
     */
    public RockeyUserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.active = user.getStatus() == UserStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    /**
     * Converts the stored security role into Spring's ROLE_ authority format for route authorization.
     * Employee jobRole is not an authority.
     */
    // Implements the inherited Java/Spring contract rather than defining a separate callback.
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    // Implements the inherited Java/Spring contract rather than defining a separate callback.
    @Override
    public String getPassword() {
        return passwordHash;
    }

    // Implements the inherited Java/Spring contract rather than defining a separate callback.
    @Override
    public String getUsername() {
        return email;
    }

    // Implements the inherited Java/Spring contract rather than defining a separate callback.
    @Override
    public boolean isEnabled() {
        return active;
    }
}

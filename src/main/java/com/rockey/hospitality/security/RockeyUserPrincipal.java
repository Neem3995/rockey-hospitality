package com.rockey.hospitality.security;

import com.rockey.hospitality.entity.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * STUDY NOTE: A principal is the authenticated identity Spring Security passes to controllers.
 * This class adapts User to Spring's UserDetails contract and maps USER/STAFF/ADMIN to role authorities
 * used for authorization.
 * Employee jobRole and Department describe work context, not security permissions.
 * The password hash stays internal for authentication and is not a response DTO field.
 */
public class RockeyUserPrincipal implements UserDetails {

    // @Override shows that this method implements a superclass/interface contract rather than inventing a
    // separate hook.

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
    private final User.Role role;
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
        this.active = user.getStatus() == User.Status.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public User.Role getRole() {
        return role;
    }

    /**
     * Converts the stored security role into Spring's ROLE_ authority format for route authorization.
     * Employee jobRole is not an authority.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}

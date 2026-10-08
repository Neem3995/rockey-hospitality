package com.rockey.hospitality.security;

import com.rockey.hospitality.entity.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * STUDY NOTE: This object adapts a stored User to Spring Security's UserDetails contract.
 * The account loader constructs it for JWT verification, copying id, email, role, active and hash.
 * getAuthorities adds ROLE_ to the enum for SecurityConfiguration's role matchers.
 * Controllers receive this authenticated principal, not a user id chosen in the JSON body.
 * This is an internal identity object, not a response DTO; its password hash must not reach the UI.
 */
public class RockeyUserPrincipal implements UserDetails {
    private final Long id;
    private final String email;
    private final String passwordHash;
    private final User.Role role;
    private final boolean active;
    public RockeyUserPrincipal(User user) {
        id = user.getId(); email = user.getEmail(); passwordHash = user.getPasswordHash();
        role = user.getRole(); active = user.isActive();
    }
    public Long getId() { return id; }
    public User.Role getRole() { return role; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return active; }
}

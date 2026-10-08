package com.rockey.hospitality.security;

import com.rockey.hospitality.entity.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * STUDY NOTE: A principal adapts the current database account to Spring's UserDetails contract.
 * USER means housekeeper; MANAGER and ADMIN are authorization roles, not browser-selected labels.
 * The JWT filter reloads these fields for every request; hashes never enter response DTOs.
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

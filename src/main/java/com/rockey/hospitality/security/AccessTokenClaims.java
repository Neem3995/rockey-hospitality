package com.rockey.hospitality.security;

import com.rockey.hospitality.entity.Role;

/**
 * Carries the identity claims extracted from a successfully verified access JWT.
 * JwtAuthenticationFilter compares them with the current database account.
 */
public class AccessTokenClaims {

    /**
     * Signed userId claim; the filter requires it to match the current database User ID.
     */
    private final Long userId;
    /**
     * Signed subject claim (login email) used to reload the current database User.
     */
    private final String email;
    /**
     * Signed role claim; the filter rejects the token if the database role has changed.
     */
    private final Role role;

    /**
     * Packages verified identity claims for comparison with the current database User.
     */
    public AccessTokenClaims(Long userId, String email, Role role) {
        this.userId = userId;
        this.email = email;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }
}

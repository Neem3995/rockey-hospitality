package com.rockey.hospitality.security;

import com.rockey.hospitality.entity.Role;

public class AccessTokenClaims {

    private final Long userId;
    private final String email;
    private final Role role;

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

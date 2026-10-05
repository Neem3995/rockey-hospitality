package com.rockey.hospitality.security;

import java.time.Instant;

public class IssuedAccessToken {

    private final String value;
    private final Instant expiresAt;

    public IssuedAccessToken(String value, Instant expiresAt) {
        this.value = value;
        this.expiresAt = expiresAt;
    }

    public String getValue() {
        return value;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

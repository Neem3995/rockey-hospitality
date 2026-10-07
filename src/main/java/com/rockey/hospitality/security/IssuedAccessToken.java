package com.rockey.hospitality.security;

import java.time.Instant;

/**
 * Keeps an issued access JWT together with its expiration instant for the authentication response.
 */
public class IssuedAccessToken {

    /**
     * Signed access JWT returned in the authentication DTO for memory-only client use.
     */
    private final String value;
    /**
     * Expiration instant for this issued token.
     */
    private final Instant expiresAt;

    /**
     * Pairs the signed access JWT with its expiry instant.
     */
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

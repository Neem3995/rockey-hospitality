package com.rockey.hospitality.security;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Internal refresh-token result separating the raw cookie value from its database hash.
 * The raw value is not included in a public response DTO.
 */
public class IssuedRefreshToken {

    /**
     * Secret raw refresh value destined for the HttpOnly cookie only.
     */
    private final String rawValue;
    /**
     * SHA-256 lookup hash persisted on User instead of the raw token.
     */
    private final String hash;
    /**
     * Expiration instant for this issued token.
     */
    private final Instant expiresAt;
    /**
     * Same expiry represented as UTC LocalDateTime for database comparisons.
     */
    private final LocalDateTime databaseExpiresAt;

    /**
     * Keeps raw value, hash, and both expiry representations together for session persistence and cookie delivery.
     */
    public IssuedRefreshToken(
            String rawValue,
            String hash,
            Instant expiresAt,
            LocalDateTime databaseExpiresAt
    ) {
        this.rawValue = rawValue;
        this.hash = hash;
        this.expiresAt = expiresAt;
        this.databaseExpiresAt = databaseExpiresAt;
    }

    public String getRawValue() {
        return rawValue;
    }

    public String getHash() {
        return hash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getDatabaseExpiresAt() {
        return databaseExpiresAt;
    }
}

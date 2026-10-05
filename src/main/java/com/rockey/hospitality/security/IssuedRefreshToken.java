package com.rockey.hospitality.security;

import java.time.Instant;
import java.time.LocalDateTime;

public class IssuedRefreshToken {

    private final String rawValue;
    private final String hash;
    private final Instant expiresAt;
    private final LocalDateTime databaseExpiresAt;

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

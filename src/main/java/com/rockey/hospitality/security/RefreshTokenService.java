package com.rockey.hospitality.security;

import com.rockey.hospitality.configuration.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * STUDY NOTE: AuthService uses this bean to create a random refresh secret and its hash/expiry.
 * SecureRandom supplies the bytes; SHA-256 hashes the raw value. Only that hash goes in User.
 * AuthController sends the raw value in an HttpOnly cookie, not a JavaScript-readable response.
 * Clock computes expiry as an Instant, then converts it to UTC LocalDateTime for database comparison.
 * This helper does not load accounts or rotate stored state; AuthService owns that transaction.
 */
@Service
public class RefreshTokenService {

    /**
     * 32 secure random bytes supply entropy for each opaque refresh token.
     */
    private static final int TOKEN_BYTES = 32;

    /**
     * Cryptographically secure random source for opaque refresh values.
     */
    private final SecureRandom secureRandom;
    /**
     * Configured fixed duration used for opaque refresh-session expiry.
     */
    private final Duration refreshTokenLifetime;
    /**
     * Injected time source so expiry and automation boundaries can be controlled without waiting in real time.
     */
    private final Clock clock;

    /**
     * Uses the configured lifetime and injected Clock, supplying SecureRandom for normal token generation.
     */
    // Selects this constructor for Spring dependency injection when overloads are present.
    @Autowired
    public RefreshTokenService(SecurityProperties properties, Clock clock) {
        this(properties, clock, new SecureRandom());
    }

    /**
     * Accepts an explicit SecureRandom alongside configuration and Clock so token generation can be exercised deterministically.
     */
    RefreshTokenService(SecurityProperties properties, Clock clock, SecureRandom secureRandom) {
        this.refreshTokenLifetime = Duration.ofDays(properties.getRefreshTokenDays());
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    /**
     * Encodes 32 secure random bytes as an opaque URL-safe token, then computes its hash and expiry.
     * The database expiry is a UTC LocalDateTime representation.
     */
    public RefreshTokenService.IssuedRefreshToken issueRefreshToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawValue = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expiresAt = clock.instant().plus(refreshTokenLifetime);
        return new RefreshTokenService.IssuedRefreshToken(
                rawValue,
                hash(rawValue),
                expiresAt,
                LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
        );
    }

    /**
     * Calculates a SHA-256 hexadecimal hash for refresh-token lookup without storing the raw token.
     * A missing digest implementation is treated as a server configuration failure.
     */
    public String hash(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    /**
     * Converts the injected current instant to UTC LocalDateTime so persisted refresh expiry comparisons use the same time basis.
     */
    public LocalDateTime currentDatabaseTime() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }


    /**
     * Internal refresh-token result separating the raw cookie value from its database hash.
     * The raw value is not included in a public response DTO.
     */
    public static class IssuedRefreshToken {

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
}

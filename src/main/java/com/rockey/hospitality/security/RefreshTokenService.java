package com.rockey.hospitality.security;

import com.rockey.hospitality.configuration.SecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

/**
 * Creates cryptographically random opaque refresh tokens and their SHA-256 database hashes.
 * Refresh tokens are not access JWTs and their raw values travel only in the HttpOnly cookie.
 */
// Registers this business/security service for constructor injection.
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
    public IssuedRefreshToken issueRefreshToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawValue = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expiresAt = clock.instant().plus(refreshTokenLifetime);
        return new IssuedRefreshToken(
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
}

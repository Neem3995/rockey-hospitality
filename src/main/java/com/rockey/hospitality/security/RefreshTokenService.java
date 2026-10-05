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

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;
    private final Duration refreshTokenLifetime;
    private final Clock clock;

    @Autowired
    public RefreshTokenService(SecurityProperties properties, Clock clock) {
        this(properties, clock, new SecureRandom());
    }

    RefreshTokenService(SecurityProperties properties, Clock clock, SecureRandom secureRandom) {
        this.refreshTokenLifetime = Duration.ofDays(properties.getRefreshTokenDays());
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

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

    public String hash(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public LocalDateTime currentDatabaseTime() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}

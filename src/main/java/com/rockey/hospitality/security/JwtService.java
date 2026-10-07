package com.rockey.hospitality.security;

import com.rockey.hospitality.configuration.SecurityProperties;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Signs short-lived access JWTs and verifies incoming ones.
 * Instant and Clock handle time internally; Date conversion is limited to the JJWT API boundary.
 */
// Registers this business/security service for constructor injection.
@Service
public class JwtService {

    /**
     * HMAC key decoded from private Base64 configuration, never embedded in source or responses.
     */
    private final SecretKey signingKey;
    /**
     * Configured fixed duration used when signing access JWTs.
     */
    private final Duration accessTokenLifetime;
    /**
     * Injected time source so expiry and automation boundaries can be controlled without waiting in real time.
     */
    private final Clock clock;

    /**
     * Decodes the configured HMAC key and fixes the access-token lifetime, using an injected Clock rather than hidden time calls.
     */
    public JwtService(SecurityProperties properties, Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getJwtSecret()));
        this.accessTokenLifetime = Duration.ofMinutes(properties.getAccessTokenMinutes());
        this.clock = clock;
    }

    /**
     * Signs the User's email, ID, and role with issue and expiration times from the injected Clock.
     * Returns the JWT and its expiration for AuthService.
     */
    public IssuedAccessToken issueAccessToken(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(accessTokenLifetime);
        String token = Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();

        return new IssuedAccessToken(token, expiresAt);
    }

    /**
     * Verifies the signature and expiration before extracting required identity claims.
     * Missing claims or an invalid role prevent authentication.
     */
    public AccessTokenClaims parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Number userId = claims.get("userId", Number.class);
        String role = claims.get("role", String.class);
        String subject = claims.getSubject();
        if (userId == null
                || subject == null
                || subject.isBlank()
                || role == null
                || role.isBlank()) {
            throw new MalformedJwtException("Access token is missing required claims.");
        }
        return new AccessTokenClaims(
                userId.longValue(),
                subject,
                Role.valueOf(role)
        );
    }
}

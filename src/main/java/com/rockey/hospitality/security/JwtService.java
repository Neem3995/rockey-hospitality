package com.rockey.hospitality.security;

import com.rockey.hospitality.configuration.SecurityProperties;
import com.rockey.hospitality.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * STUDY NOTE: A JWT is a signed token used to identify authenticated API requests; an access JWT is
 * short-lived and sent in the Authorization header.
 * Here, @Service lets AuthService and JwtAuthenticationFilter share this signing/verification component.
 * It uses the private configured key and injected Clock; java.util.Date conversion occurs only at the JJWT
 * API boundary.
 * A valid signature does not replace the filter's current-database identity checks or service
 * authorization.
 */
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
    public JwtService.IssuedAccessToken issueAccessToken(User user) {
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

        return new JwtService.IssuedAccessToken(token, expiresAt);
    }

    /**
     * Verifies the signature and expiration before extracting required identity claims.
     * Missing claims or an invalid role prevent authentication.
     */
    public JwtService.AccessTokenClaims parseAccessToken(String token) {
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
        return new JwtService.AccessTokenClaims(
                userId.longValue(),
                subject,
                User.Role.valueOf(role)
        );
    }


    /**
     * Carries the identity claims extracted from a successfully verified access JWT.
     * JwtAuthenticationFilter compares them with the current database account.
     */
    public static class AccessTokenClaims {

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
        private final User.Role role;

        /**
         * Packages verified identity claims for comparison with the current database User.
         */
        public AccessTokenClaims(Long userId, String email, User.Role role) {
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

        public User.Role getRole() {
            return role;
        }
    }

    /**
     * Keeps an issued access JWT together with its expiration instant for the authentication response.
     */
    public static class IssuedAccessToken {

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
}

package com.rockey.hospitality.security;

import com.rockey.hospitality.configuration.SecurityProperties;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    @Test
    void issuedTokenContainsOnlyRequiredIdentityAndRoleClaims() {
        Instant now = Instant.parse("2026-10-03T20:00:00Z");
        JwtService jwtService = new JwtService(
                properties(),
                Clock.fixed(now, ZoneOffset.UTC)
        );
        User user = user(12L, Role.ADMIN);

        IssuedAccessToken token = jwtService.issueAccessToken(user);
        AccessTokenClaims claims = jwtService.parseAccessToken(token.getValue());

        assertThat(claims.getUserId()).isEqualTo(12L);
        assertThat(claims.getEmail()).isEqualTo("admin@example.test");
        assertThat(claims.getRole()).isEqualTo(Role.ADMIN);
        assertThat(token.getExpiresAt()).isEqualTo(now.plusSeconds(15 * 60));
    }

    @Test
    void expiredTokenIsRejected() {
        SecurityProperties properties = properties();
        JwtService issuingService = new JwtService(
                properties,
                Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)
        );
        String token = issuingService.issueAccessToken(user(12L, Role.USER)).getValue();
        JwtService validatingService = new JwtService(
                properties,
                Clock.fixed(Instant.parse("2020-01-01T00:16:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> validatingService.parseAccessToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void malformedTokenIsRejected() {
        JwtService jwtService = new JwtService(properties(), Clock.systemUTC());

        assertThatThrownBy(() -> jwtService.parseAccessToken("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void signedTokenMissingRequiredClaimsIsRejected() {
        Instant now = Instant.parse("2026-10-03T20:00:00Z");
        SecurityProperties properties = properties();
        JwtService jwtService = new JwtService(
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        String token = Jwts.builder()
                .subject("user@example.test")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getJwtSecret())))
                .compact();

        assertThatThrownBy(() -> jwtService.parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }

    private SecurityProperties properties() {
        SecurityProperties properties = new SecurityProperties();
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        properties.setJwtSecret(Base64.getEncoder().encodeToString(keyBytes));
        properties.setAccessTokenMinutes(15);
        return properties;
    }

    private User user(Long id, Role role) {
        User user = new User("Admin User", "admin@example.test", "bcrypt-hash");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }
}

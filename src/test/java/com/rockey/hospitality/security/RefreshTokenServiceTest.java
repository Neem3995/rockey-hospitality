package com.rockey.hospitality.security;

import com.rockey.hospitality.configuration.SecurityProperties;
import com.rockey.hospitality.security.RefreshTokenService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenServiceTest {

    @Test
    void issuesOpaqueTokenAndOnlyDeterministicHashNeedsPersistence() {
        SecurityProperties properties = new SecurityProperties();
        properties.setRefreshTokenDays(7);
        Instant now = Instant.parse("2026-10-03T20:00:00Z");
        RefreshTokenService service = new RefreshTokenService(
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        RefreshTokenService.IssuedRefreshToken token = service.issueRefreshToken();

        assertThat(token.getRawValue()).isNotBlank();
        assertThat(token.getRawValue()).doesNotContain(".");
        assertThat(token.getHash()).hasSize(64).isNotEqualTo(token.getRawValue());
        assertThat(service.hash(token.getRawValue())).isEqualTo(token.getHash());
        assertThat(token.getExpiresAt()).isEqualTo(now.plusSeconds(7 * 24 * 60 * 60));
    }
}

package com.rockey.hospitality.security;

import com.rockey.hospitality.exception.ApiException.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticationRateLimiterTest {

    private MutableClock clock;
    private AuthenticationRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-03T20:00:00Z"));
        rateLimiter = new AuthenticationRateLimiter(clock);
    }

    @Test
    void blocksLoginAfterFiveFailuresAndAllowsResetAfterSuccess() {
        for (int attempt = 0; attempt < 5; attempt++) {
            rateLimiter.checkLoginAllowed("127.0.0.1", "user@example.test");
            rateLimiter.recordLoginFailure("127.0.0.1", "user@example.test");
        }

        assertThatThrownBy(() -> rateLimiter.checkLoginAllowed(
                "127.0.0.1",
                "user@example.test"
        )).isInstanceOf(RateLimitExceededException.class);

        rateLimiter.resetLoginFailures("127.0.0.1", "user@example.test");
        assertThatCode(() -> rateLimiter.checkLoginAllowed(
                "127.0.0.1",
                "user@example.test"
        )).doesNotThrowAnyException();
    }

    @Test
    void loginFailureWindowExpiresAfterFifteenMinutes() {
        for (int attempt = 0; attempt < 5; attempt++) {
            rateLimiter.recordLoginFailure("127.0.0.1", "user@example.test");
        }

        clock.advance(Duration.ofMinutes(15));

        assertThatCode(() -> rateLimiter.checkLoginAllowed(
                "127.0.0.1",
                "user@example.test"
        )).doesNotThrowAnyException();
    }

    @Test
    void registrationAllowsFiveAttemptsPerHour() {
        for (int attempt = 0; attempt < 5; attempt++) {
            rateLimiter.consumeRegistrationAttempt("127.0.0.1");
        }

        assertThatThrownBy(() -> rateLimiter.consumeRegistrationAttempt("127.0.0.1"))
                .isInstanceOf(RateLimitExceededException.class);

        clock.advance(Duration.ofHours(1));
        assertThatCode(() -> rateLimiter.consumeRegistrationAttempt("127.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void refreshAllowsThirtyAttemptsPerFifteenMinutes() {
        for (int attempt = 0; attempt < 30; attempt++) {
            rateLimiter.consumeRefreshAttempt("127.0.0.1");
        }

        assertThatThrownBy(() -> rateLimiter.consumeRefreshAttempt("127.0.0.1"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    private static class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

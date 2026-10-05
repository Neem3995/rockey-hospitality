package com.rockey.hospitality.security;

import com.rockey.hospitality.exception.RateLimitExceededException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthenticationRateLimiter {

    private static final int LOGIN_FAILURE_LIMIT = 5;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final int REGISTRATION_LIMIT = 5;
    private static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);
    private static final int REFRESH_LIMIT = 30;
    private static final Duration REFRESH_WINDOW = Duration.ofMinutes(15);

    private final Map<String, AttemptWindow> loginFailures = new ConcurrentHashMap<>();
    private final Map<String, AttemptWindow> registrationAttempts = new ConcurrentHashMap<>();
    private final Map<String, AttemptWindow> refreshAttempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public AuthenticationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void checkLoginAllowed(String clientIp, String normalizedEmail) {
        String key = clientIp + "|" + normalizedEmail;
        AttemptWindow window = loginFailures.get(key);
        if (window == null) {
            return;
        }
        if (window.isExpired(clock.instant(), LOGIN_WINDOW)) {
            loginFailures.remove(key, window);
            return;
        }
        if (window.getAttempts() >= LOGIN_FAILURE_LIMIT) {
            throw new RateLimitExceededException("Too many failed login attempts. Try again later.");
        }
    }

    public void recordLoginFailure(String clientIp, String normalizedEmail) {
        recordAttempt(loginFailures, clientIp + "|" + normalizedEmail, LOGIN_WINDOW);
    }

    public void resetLoginFailures(String clientIp, String normalizedEmail) {
        loginFailures.remove(clientIp + "|" + normalizedEmail);
    }

    public void consumeRegistrationAttempt(String clientIp) {
        consume(
                registrationAttempts,
                clientIp,
                REGISTRATION_LIMIT,
                REGISTRATION_WINDOW,
                "Too many registration attempts. Try again later."
        );
    }

    public void consumeRefreshAttempt(String clientIp) {
        consume(
                refreshAttempts,
                clientIp,
                REFRESH_LIMIT,
                REFRESH_WINDOW,
                "Too many refresh attempts. Try again later."
        );
    }

    private void consume(
            Map<String, AttemptWindow> attempts,
            String key,
            int limit,
            Duration windowLength,
            String message
    ) {
        Instant now = clock.instant();
        AttemptDecision decision = new AttemptDecision();
        attempts.compute(key, (ignored, current) -> {
            if (current == null || current.isExpired(now, windowLength)) {
                decision.allowed = true;
                return new AttemptWindow(now, 1);
            }
            if (current.getAttempts() >= limit) {
                decision.allowed = false;
                return current;
            }
            decision.allowed = true;
            return current.incremented();
        });

        if (!decision.allowed) {
            throw new RateLimitExceededException(message);
        }
    }

    private void recordAttempt(
            Map<String, AttemptWindow> attempts,
            String key,
            Duration windowLength
    ) {
        Instant now = clock.instant();
        attempts.compute(key, (ignored, current) -> {
            if (current == null || current.isExpired(now, windowLength)) {
                return new AttemptWindow(now, 1);
            }
            return current.incremented();
        });
    }

    private static class AttemptDecision {
        private boolean allowed;
    }

    private static class AttemptWindow {
        private final Instant startedAt;
        private final int attempts;

        private AttemptWindow(Instant startedAt, int attempts) {
            this.startedAt = startedAt;
            this.attempts = attempts;
        }

        private AttemptWindow incremented() {
            return new AttemptWindow(startedAt, attempts + 1);
        }

        private boolean isExpired(Instant now, Duration duration) {
            return !now.isBefore(startedAt.plus(duration));
        }

        private int getAttempts() {
            return attempts;
        }
    }
}

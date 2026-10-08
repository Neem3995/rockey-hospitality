package com.rockey.hospitality.security;

import com.rockey.hospitality.exception.ApiException.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * STUDY NOTE: AuthService asks this shared object whether another auth attempt is allowed.
 * IP/email keys and the injected Clock identify counters/windows; excess attempts raise a 429 failure.
 * Registration/refresh consume attempts; failed login records a failure and successful login clears it.
 * ConcurrentHashMap protects per-key counter updates within this JVM. Counters are not persisted
 * or shared across servers, and this limiter is separate from a database transaction rollback.
 */
@Service
public class AuthenticationRateLimiter {

    /**
     * Five failed logins per IP/email window.
     */
    private static final int LOGIN_FAILURE_LIMIT = 5;
    /**
     * Fifteen-minute window for failed logins.
     */
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    /**
     * Five registration attempts per IP window.
     */
    private static final int REGISTRATION_LIMIT = 5;
    /**
     * One-hour window for registration attempts.
     */
    private static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);
    /**
     * Thirty refresh attempts per IP window.
     */
    private static final int REFRESH_LIMIT = 30;
    /**
     * Fifteen-minute window for refresh attempts.
     */
    private static final Duration REFRESH_WINDOW = Duration.ofMinutes(15);

    /**
     * Process-local failure windows keyed by client IP and normalized login email.
     */
    private final Map<String, AttemptWindow> loginFailures = new ConcurrentHashMap<>();
    /**
     * Process-local registration-attempt windows keyed by client IP.
     */
    private final Map<String, AttemptWindow> registrationAttempts = new ConcurrentHashMap<>();
    /**
     * Process-local refresh-attempt windows keyed by client IP.
     */
    private final Map<String, AttemptWindow> refreshAttempts = new ConcurrentHashMap<>();
    /**
     * We inject time so tests can check auth-window expiry without waiting in real time.
     */
    private final Clock clock;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public AuthenticationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Rejects an IP/email pair after five failures within fifteen minutes.
     * Expired windows are removed so that pair can try again.
     */
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

    /**
     * Adds one failure to the IP/email window without changing successful-login or registration counters.
     */
    public void recordLoginFailure(String clientIp, String normalizedEmail) {
        recordAttempt(loginFailures, clientIp + "|" + normalizedEmail, LOGIN_WINDOW);
    }

    /**
     * Clears the IP/email failure window after a successful login.
     */
    public void resetLoginFailures(String clientIp, String normalizedEmail) {
        loginFailures.remove(clientIp + "|" + normalizedEmail);
    }

    /**
     * Consumes one of five registration attempts per client IP in a one-hour window.
     */
    public void consumeRegistrationAttempt(String clientIp) {
        consume(
                registrationAttempts,
                clientIp,
                REGISTRATION_LIMIT,
                REGISTRATION_WINDOW,
                "Too many registration attempts. Try again later."
        );
    }

    /**
     * Consumes one of thirty refresh attempts per client IP in a fifteen-minute window.
     */
    public void consumeRefreshAttempt(String clientIp) {
        consume(
                refreshAttempts,
                clientIp,
                REFRESH_LIMIT,
                REFRESH_WINDOW,
                "Too many refresh attempts. Try again later."
        );
    }

    /**
     * Atomically checks and increments a rate-limit window using compute.
     * A denied attempt leaves the existing counter unchanged and raises a 429 error.
     */
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

    /**
     * Atomically increments a failure count, starting a new window when the previous one has expired.
     */
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

    /**
     * Carries the allow/deny result out of a concurrent-map update so the caller can return a rate-limit error.
     */
    private static class AttemptDecision {
        /**
         * Result of the atomic window check, read after compute finishes.
         */
        private boolean allowed;
    }

    /**
     * Stores an immutable counter and its starting instant.
     * Replacing the window makes increments safe inside ConcurrentHashMap.compute.
     */
    private static class AttemptWindow {
        /**
         * Start instant retained throughout one attempt window.
         */
        private final Instant startedAt;
        /**
         * Number of consumed attempts or recorded failures in this immutable window.
         */
        private final int attempts;

        /**
         * Creates an immutable attempt count anchored at one start instant.
         */
        private AttemptWindow(Instant startedAt, int attempts) {
            this.startedAt = startedAt;
            this.attempts = attempts;
        }

        /**
         * Returns a replacement window with the original start time and one additional attempt.
         */
        private AttemptWindow incremented() {
            return new AttemptWindow(startedAt, attempts + 1);
        }

        /**
         * Treats the window as expired at or after its exact end instant.
         */
        private boolean isExpired(Instant now, Duration duration) {
            return !now.isBefore(startedAt.plus(duration));
        }

        private int getAttempts() {
            return attempts;
        }
    }
}

package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.AuthDtos.*;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.exception.ApiException.*;
import com.rockey.hospitality.repository.UserRepository;
import com.rockey.hospitality.security.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STUDY NOTE: AuthController calls this service with validated credentials, a refresh secret or caller id.
 * First we check rate limits/accounts, then coordinate BCrypt, JWT and refresh helpers with UserRepository.
 * Public registration creates USER. Login and successful refresh replace the row's one refresh session;
 * another tab/device's old refresh secret then fails. Logout clears that hash/expiry pair.
 * Account/session writes are transactional; limiter counters are separate in-memory state.
 * We return an internal AuthSession. AuthController sends only its safe response in JSON and its
 * raw refresh value as a cookie. Logout does not blacklist already-issued access JWTs.
 */
@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final AuthenticationRateLimiter limiter;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtService jwt,
                       RefreshTokenService refreshTokens, AuthenticationRateLimiter limiter) {
        this.users = users; this.passwords = passwords; this.jwt = jwt;
        this.refreshTokens = refreshTokens; this.limiter = limiter;
    }

    @Transactional
    public AuthSession register(String name, String email, String password, String clientIp) {
        limiter.consumeRegistrationAttempt(clientIp);
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(normalized)) throw new ConflictException("Email is already registered.");
        User user = users.save(new User(name.trim(), normalized, hashPassword(password)));
        return startSession(user);
    }

    @Transactional
    public AuthSession login(String email, String password, String clientIp) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        limiter.checkLoginAllowed(clientIp, normalized);
        User user = users.findByEmailIgnoreCase(normalized).orElse(null);
        if (user == null || !user.isActive() || !passwords.matches(password, user.getPasswordHash())) {
            limiter.recordLoginFailure(clientIp, normalized);
            throw new InvalidCredentialsException();
        }
        limiter.resetLoginFailures(clientIp, normalized);
        return startSession(user);
    }

    @Transactional
    public AuthSession refresh(String raw, String clientIp) {
        limiter.consumeRefreshAttempt(clientIp);
        if (raw == null || raw.isBlank()) throw new InvalidRefreshTokenException();
        // The locked hash lookup ensures only one concurrent request can rotate this session.
        User user = users.findByRefreshTokenHash(refreshTokens.hash(raw))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!user.isActive() || user.getRefreshTokenExpiresAt() == null
                || !user.getRefreshTokenExpiresAt().isAfter(refreshTokens.currentDatabaseTime())) {
            throw new InvalidRefreshTokenException();
        }
        return startSession(user);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Long id) {
        User user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User account not found."));
        if (!user.isActive()) throw new InvalidCredentialsException();
        return profile(user);
    }

    @Transactional
    public void logout(Long id) {
        User user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User account not found."));
        user.clearRefreshSession();
        users.save(user);
    }

    /** BCrypt has a byte limit, not just a character limit. No password value is ever returned. */
    public String hashPassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Password must not exceed 72 UTF-8 bytes.");
        }
        return passwords.encode(password);
    }

    private AuthSession startSession(User user) {
        var refresh = refreshTokens.issueRefreshToken();
        user.replaceRefreshSession(refresh.getHash(), refresh.getDatabaseExpiresAt());
        users.save(user);
        var access = jwt.issueAccessToken(user);
        return new AuthSession(new AuthResponse(access.getValue(), "Bearer", access.getExpiresAt(),
                refresh.getExpiresAt(), profile(user)), refresh.getRawValue());
    }

    private CurrentUserResponse profile(User user) {
        return new CurrentUserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive());
    }

    /** Internal result separates the safe JSON response from the HttpOnly cookie value. */
    public static class AuthSession {
        private final AuthResponse response;
        private final String rawRefreshToken;
        public AuthSession(AuthResponse response, String rawRefreshToken) {
            this.response = response; this.rawRefreshToken = rawRefreshToken;
        }
        public AuthResponse getResponse() { return response; }
        public String getRawRefreshToken() { return rawRefreshToken; }
    }
}

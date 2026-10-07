package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.auth.AuthResponse;
import com.rockey.hospitality.dto.auth.CurrentUserResponse;
import com.rockey.hospitality.dto.auth.DepartmentSummary;
import com.rockey.hospitality.entity.Department;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.entity.UserStatus;
import com.rockey.hospitality.exception.ConflictException;
import com.rockey.hospitality.exception.InvalidCredentialsException;
import com.rockey.hospitality.exception.InvalidRefreshTokenException;
import com.rockey.hospitality.exception.ResourceNotFoundException;
import com.rockey.hospitality.repository.UserRepository;
import com.rockey.hospitality.repository.EmployeeRepository;
import com.rockey.hospitality.security.AuthenticationRateLimiter;
import com.rockey.hospitality.security.IssuedAccessToken;
import com.rockey.hospitality.security.IssuedRefreshToken;
import com.rockey.hospitality.security.JwtService;
import com.rockey.hospitality.security.RefreshTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Coordinates registration, BCrypt login, refresh rotation, profile reads, and logout.
 * Only a hash and expiration are persisted for the single active refresh session.
 */
// Registers this business/security service for constructor injection.
@Service
public class AuthService {

    /**
     * Injected UserRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final UserRepository userRepository;
    /**
     * Injected EmployeeRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final EmployeeRepository employeeRepository;
    /**
     * Shared BCrypt encoder for password creation and credential matching.
     */
    private final PasswordEncoder passwordEncoder;
    /**
     * Injected JwtService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final JwtService jwtService;
    /**
     * Injected RefreshTokenService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final RefreshTokenService refreshTokenService;
    /**
     * Injected process-local attempt limiter for login, registration, and refresh abuse prevention.
     */
    private final AuthenticationRateLimiter rateLimiter;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public AuthService(
            UserRepository userRepository,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            AuthenticationRateLimiter rateLimiter
    ) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Limits registration attempts, normalizes email, and rejects duplicates before storing a BCrypt hash.
     * The new User keeps its default USER role and starts a refresh session.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes.
    @Transactional
    public AuthSession register(
            String name,
            String email,
            String password,
            String clientIp
    ) {
        rateLimiter.consumeRegistrationAttempt(clientIp);
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Email is already registered.");
        }

        User user = new User(
                name.trim(),
                normalizedEmail,
                passwordEncoder.encode(password)
        );
        userRepository.save(user);
        return startSession(user);
    }

    /**
     * Checks the IP/email failure limit and verifies an active User through BCrypt matching.
     * Successful login resets failures and rotates the refresh session.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes.
    @Transactional
    public AuthSession login(String email, String password, String clientIp) {
        String normalizedEmail = normalizeEmail(email);
        rateLimiter.checkLoginAllowed(clientIp, normalizedEmail);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);

        if (user == null
                || user.getStatus() != UserStatus.ACTIVE
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            rateLimiter.recordLoginFailure(clientIp, normalizedEmail);
            throw new InvalidCredentialsException();
        }

        rateLimiter.resetLoginFailures(clientIp, normalizedEmail);
        return startSession(user);
    }

    /**
     * Limits refresh attempts and looks up the hash of the cookie token.
     * Only an active account with an unexpired stored session can rotate into new access and refresh tokens.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes.
    @Transactional
    public AuthSession refresh(String rawRefreshToken, String clientIp) {
        rateLimiter.consumeRefreshAttempt(clientIp);
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        String hash = refreshTokenService.hash(rawRefreshToken);
        User user = userRepository.findByRefreshTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (user.getStatus() != UserStatus.ACTIVE
                || user.getRefreshTokenExpiresAt() == null
                || !user.getRefreshTokenExpiresAt().isAfter(
                        refreshTokenService.currentDatabaseTime()
                )) {
            throw new InvalidRefreshTokenException();
        }

        return startSession(user);
    }

    /**
     * Loads the current active account and returns its safe profile DTO; inactive or missing accounts do not receive profile data.
     */
    // Runs this service operation in a read-only transaction, keeping lazy reads and DTO mapping inside the persistence boundary.
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }
        return toCurrentUserResponse(user);
    }

    /**
     * Clears the stored refresh hash and expiration even when they are already empty.
     * It revokes refresh capability, not an already issued access JWT.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes.
    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found."));
        user.clearRefreshSession();
        userRepository.save(user);
    }

    /**
     * Replaces the one stored refresh session and issues a short-lived access JWT.
     * The raw refresh token stays separate from the JSON response for cookie delivery.
     */
    private AuthSession startSession(User user) {
        // Replace the stored hash each time: rotating refresh permits only one active session.
        IssuedRefreshToken refreshToken = refreshTokenService.issueRefreshToken();
        user.replaceRefreshSession(
                refreshToken.getHash(),
                refreshToken.getDatabaseExpiresAt()
        );
        userRepository.save(user);

        IssuedAccessToken accessToken = jwtService.issueAccessToken(user);
        AuthResponse response = new AuthResponse(
                accessToken.getValue(),
                "Bearer",
                accessToken.getExpiresAt(),
                refreshToken.getExpiresAt(),
                toCurrentUserResponse(user)
        );
        return new AuthSession(response, refreshToken.getRawValue());
    }

    /**
     * Maps safe account fields and optional Department/Employee references without returning password or refresh-token hashes.
     */
    private CurrentUserResponse toCurrentUserResponse(User user) {
        Department department = user.getDepartment();
        DepartmentSummary departmentSummary = department == null
                ? null
                : new DepartmentSummary(department.getId(), department.getName());
        Long employeeId = user.getId() == null
                ? null
                : employeeRepository.findByUserId(user.getId())
                .map(Employee::getId)
                .orElse(null);

        return new CurrentUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                departmentSummary,
                user.getStatus(),
                employeeId
        );
    }

    /**
     * Trims and lowercases email with Locale.ROOT for consistent account lookup and uniqueness checks.
     */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

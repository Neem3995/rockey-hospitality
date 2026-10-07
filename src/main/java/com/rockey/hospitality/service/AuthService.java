package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.AuthDtos.AuthResponse;
import com.rockey.hospitality.dto.AuthDtos.CurrentUserResponse;
import com.rockey.hospitality.dto.AuthDtos.DepartmentSummary;
import com.rockey.hospitality.entity.Department;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.exception.ApiException.ConflictException;
import com.rockey.hospitality.exception.ApiException.InvalidCredentialsException;
import com.rockey.hospitality.exception.ApiException.InvalidRefreshTokenException;
import com.rockey.hospitality.exception.ApiException.ResourceNotFoundException;
import com.rockey.hospitality.repository.EmployeeRepository;
import com.rockey.hospitality.repository.UserRepository;
import com.rockey.hospitality.security.AuthenticationRateLimiter;
import com.rockey.hospitality.security.JwtService;
import com.rockey.hospitality.security.RefreshTokenService;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STUDY NOTE: A Service holds business rules and coordinates an application workflow.
 * Here, @Service lets Spring manage and inject this component; @Transactional groups database work so unchecked
 * failures roll back writes.
 * AuthService registers USER accounts, matches BCrypt passwords, rotates refresh sessions and revokes them
 * on logout.
 * AuthController delegates here; User/Employee repositories and token services provide the persisted data
 * through JPA/Hibernate.
 */
@Service
public class AuthService {

    // Transaction study key: Spring applies @Transactional when another component calls this managed service.
    // readOnly=true requests a read-oriented transaction; it keeps lazy reads and DTO mapping inside the
    // persistence boundary.
    // readOnly is not an authorization rule; repositories still run only after the service's scope checks.
    // BCrypt hashes a password; PasswordEncoder.matches compares a login attempt with that hash, not a
    // decrypted password.
    // Rotation replaces the one stored refresh hash; logout clears its hash/expiry without blacklisting
    // existing access JWTs.

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
    @Transactional
    public AuthService.AuthSession register(
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
    @Transactional
    public AuthService.AuthSession login(String email, String password, String clientIp) {
        String normalizedEmail = normalizeEmail(email);
        rateLimiter.checkLoginAllowed(clientIp, normalizedEmail);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);

        if (user == null
                || user.getStatus() != User.Status.ACTIVE
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
    @Transactional
    public AuthService.AuthSession refresh(String rawRefreshToken, String clientIp) {
        rateLimiter.consumeRefreshAttempt(clientIp);
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        String hash = refreshTokenService.hash(rawRefreshToken);
        User user = userRepository.findByRefreshTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (user.getStatus() != User.Status.ACTIVE
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
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found."));
        if (user.getStatus() != User.Status.ACTIVE) {
            throw new InvalidCredentialsException();
        }
        return toCurrentUserResponse(user);
    }

    /**
     * Clears the stored refresh hash and expiration even when they are already empty.
     * It revokes refresh capability, not an already issued access JWT.
     */
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
    private AuthService.AuthSession startSession(User user) {
        // Replace the stored hash each time: rotating refresh permits only one active session.
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issueRefreshToken();
        user.replaceRefreshSession(
                refreshToken.getHash(),
                refreshToken.getDatabaseExpiresAt()
        );
        userRepository.save(user);

        JwtService.IssuedAccessToken accessToken = jwtService.issueAccessToken(user);
        AuthResponse response = new AuthResponse(
                accessToken.getValue(),
                "Bearer",
                accessToken.getExpiresAt(),
                refreshToken.getExpiresAt(),
                toCurrentUserResponse(user)
        );
        return new AuthService.AuthSession(response, refreshToken.getRawValue());
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


    /**
     * Internal service result carrying the safe JSON response separately from the raw refresh cookie value.
     */
    public static class AuthSession {

        /**
         * Safe access-token and profile JSON response; raw refresh state is carried separately.
         */
        private final AuthResponse response;
        /**
         * Internal raw cookie value; the controller sends it only as an HttpOnly cookie, not response JSON.
         */
        private final String rawRefreshToken;

        /**
         * Packages the safe JSON DTO and secret refresh-cookie value separately for the controller.
         */
        public AuthSession(AuthResponse response, String rawRefreshToken) {
            this.response = response;
            this.rawRefreshToken = rawRefreshToken;
        }

        public AuthResponse getResponse() {
            return response;
        }

        public String getRawRefreshToken() {
            return rawRefreshToken;
        }
    }
}

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

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationRateLimiter rateLimiter;

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

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }
        return toCurrentUserResponse(user);
    }

    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found."));
        user.clearRefreshSession();
        userRepository.save(user);
    }

    private AuthSession startSession(User user) {
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

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

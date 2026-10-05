package com.rockey.hospitality.service;

import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.Department;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.entity.UserStatus;
import com.rockey.hospitality.exception.ConflictException;
import com.rockey.hospitality.exception.InvalidCredentialsException;
import com.rockey.hospitality.exception.InvalidRefreshTokenException;
import com.rockey.hospitality.repository.UserRepository;
import com.rockey.hospitality.repository.EmployeeRepository;
import com.rockey.hospitality.security.AuthenticationRateLimiter;
import com.rockey.hospitality.security.IssuedAccessToken;
import com.rockey.hospitality.security.IssuedRefreshToken;
import com.rockey.hospitality.security.JwtService;
import com.rockey.hospitality.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant ACCESS_EXPIRY = Instant.parse("2026-10-03T20:15:00Z");
    private static final Instant REFRESH_EXPIRY = Instant.parse("2026-10-10T20:00:00Z");
    private static final LocalDateTime DATABASE_REFRESH_EXPIRY =
            LocalDateTime.of(2026, 10, 10, 20, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuthenticationRateLimiter rateLimiter;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                employeeRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                rateLimiter
        );
    }

    @Test
    void registerCreatesNormalizedUserWithBcryptHashAndUserRole() {
        when(userRepository.existsByEmailIgnoreCase("guest@example.test")).thenReturn(false);
        when(passwordEncoder.encode("valid-password")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                ReflectionTestUtils.setField(user, "id", 7L);
            }
            return user;
        });
        prepareSuccessfulSession();

        AuthSession session = authService.register(
                "  Guest User  ",
                " GUEST@Example.Test ",
                "valid-password",
                "127.0.0.1"
        );

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        User saved = captor.getAllValues().get(0);
        assertThat(saved.getName()).isEqualTo("Guest User");
        assertThat(saved.getEmail()).isEqualTo("guest@example.test");
        assertThat(saved.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getDepartment()).isNull();
        assertThat(session.getRawRefreshToken()).isEqualTo("raw-refresh-token");
        assertThat(session.getResponse().getUser().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void registerRejectsDuplicateNormalizedEmail() {
        when(userRepository.existsByEmailIgnoreCase("guest@example.test")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                "Guest User",
                " GUEST@example.test ",
                "valid-password",
                "127.0.0.1"
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Email is already registered.");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginCreatesSessionAndResetsFailedAttempts() {
        User user = user(4L, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "refreshTokenHash", "previous-session-hash");
        when(userRepository.findByEmailIgnoreCase("staff@example.test"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "bcrypt-hash")).thenReturn(true);
        when(userRepository.save(user)).thenReturn(user);
        prepareSuccessfulSession();

        AuthSession session = authService.login(
                " STAFF@example.test ",
                "correct-password",
                "127.0.0.1"
        );

        verify(rateLimiter).checkLoginAllowed("127.0.0.1", "staff@example.test");
        verify(rateLimiter).resetLoginFailures("127.0.0.1", "staff@example.test");
        assertThat(user.getRefreshTokenHash()).isEqualTo("refresh-hash");
        assertThat(session.getResponse().getAccessToken()).isEqualTo("access-token");
    }

    @Test
    void loginRejectsWrongPasswordWithoutRevealingReason() {
        User user = user(4L, UserStatus.ACTIVE);
        when(userRepository.findByEmailIgnoreCase("staff@example.test"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                "staff@example.test",
                "wrong-password",
                "127.0.0.1"
        )).isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        verify(rateLimiter).recordLoginFailure("127.0.0.1", "staff@example.test");
    }

    @Test
    void loginRejectsMissingAccountWithSameSafeMessage() {
        when(userRepository.findByEmailIgnoreCase("missing@example.test"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                "missing@example.test",
                "any-password",
                "127.0.0.1"
        )).isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        verify(rateLimiter).recordLoginFailure("127.0.0.1", "missing@example.test");
    }

    @Test
    void loginRejectsInactiveAccountWithSameSafeMessage() {
        User user = user(4L, UserStatus.INACTIVE);
        when(userRepository.findByEmailIgnoreCase("staff@example.test"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(
                "staff@example.test",
                "correct-password",
                "127.0.0.1"
        )).isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void refreshRotatesStoredTokenAndIssuesNewAccessToken() {
        User user = user(4L, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "refreshTokenHash", "old-hash");
        ReflectionTestUtils.setField(
                user,
                "refreshTokenExpiresAt",
                LocalDateTime.of(2026, 10, 5, 20, 0)
        );
        when(refreshTokenService.hash("old-raw-token")).thenReturn("old-hash");
        when(refreshTokenService.currentDatabaseTime())
                .thenReturn(LocalDateTime.of(2026, 10, 3, 20, 0));
        when(userRepository.findByRefreshTokenHash(any())).thenAnswer(invocation -> {
            String requestedHash = invocation.getArgument(0);
            return requestedHash.equals(user.getRefreshTokenHash())
                    ? Optional.of(user)
                    : Optional.empty();
        });
        when(userRepository.save(user)).thenReturn(user);
        prepareSuccessfulSession();

        AuthSession session = authService.refresh("old-raw-token", "127.0.0.1");

        assertThat(user.getRefreshTokenHash()).isEqualTo("refresh-hash");
        assertThat(session.getRawRefreshToken()).isEqualTo("raw-refresh-token");
        verify(rateLimiter).consumeRefreshAttempt("127.0.0.1");

        assertThatThrownBy(() -> authService.refresh("old-raw-token", "127.0.0.1"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshRejectsExpiredSession() {
        User user = user(4L, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(
                user,
                "refreshTokenExpiresAt",
                LocalDateTime.of(2026, 10, 3, 19, 59)
        );
        when(refreshTokenService.hash("expired-token")).thenReturn("expired-hash");
        when(refreshTokenService.currentDatabaseTime())
                .thenReturn(LocalDateTime.of(2026, 10, 3, 20, 0));
        when(userRepository.findByRefreshTokenHash("expired-hash"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh("expired-token", "127.0.0.1"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(jwtService, never()).issueAccessToken(any());
    }

    @Test
    void refreshRejectsRotatedOrRevokedToken() {
        when(refreshTokenService.hash("old-token")).thenReturn("old-hash");
        when(userRepository.findByRefreshTokenHash("old-hash"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("old-token", "127.0.0.1"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Refresh session is invalid or expired.");
    }

    @Test
    void currentUserResponseContainsNoCredentialOrTokenFields() {
        User user = user(4L, UserStatus.ACTIVE);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));

        var response = authService.getCurrentUser(4L);

        assertThat(response.getEmail()).isEqualTo("staff@example.test");
        assertThat(response.getRole()).isEqualTo(Role.STAFF);
        assertThat(response.getDepartmentSummary()).isNull();
        assertThat(response.getEmployeeId()).isNull();
    }

    @Test
    void currentUserResponseIncludesLinkedEmployeeIdentity() {
        User user = user(4L, UserStatus.ACTIVE);
        Department department = new Department("Housekeeping", null);
        ReflectionTestUtils.setField(department, "id", 3L);
        user.synchronizeEmployeeDepartment(department);
        Employee employee = new Employee(
                "Staff User",
                "staff@example.test",
                department,
                "Room Attendant",
                user
        );
        ReflectionTestUtils.setField(employee, "id", 12L);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(employeeRepository.findByUserId(4L)).thenReturn(Optional.of(employee));

        var response = authService.getCurrentUser(4L);

        assertThat(response.getEmployeeId()).isEqualTo(12L);
        assertThat(response.getDepartmentSummary().getId()).isEqualTo(3L);
    }

    @Test
    void logoutClearsRefreshStateAndRevokesPreviouslyIssuedToken() {
        User user = user(4L, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "refreshTokenHash", "old-hash");
        ReflectionTestUtils.setField(
                user,
                "refreshTokenExpiresAt",
                LocalDateTime.of(2026, 10, 5, 20, 0)
        );
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(refreshTokenService.hash("old-raw-token")).thenReturn("old-hash");
        when(userRepository.findByRefreshTokenHash("old-hash")).thenAnswer(invocation ->
                "old-hash".equals(user.getRefreshTokenHash())
                        ? Optional.of(user)
                        : Optional.empty()
        );

        authService.logout(4L);

        assertThat(user.getRefreshTokenHash()).isNull();
        assertThat(user.getRefreshTokenExpiresAt()).isNull();
        assertThatThrownBy(() -> authService.refresh("old-raw-token", "127.0.0.1"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(userRepository).save(user);
    }

    @Test
    void logoutIsSafeWhenRefreshStateIsAlreadyEmpty() {
        User user = user(4L, UserStatus.ACTIVE);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        authService.logout(4L);

        assertThat(user.getRefreshTokenHash()).isNull();
        assertThat(user.getRefreshTokenExpiresAt()).isNull();
        verify(userRepository).save(user);
    }

    private void prepareSuccessfulSession() {
        when(refreshTokenService.issueRefreshToken()).thenReturn(new IssuedRefreshToken(
                "raw-refresh-token",
                "refresh-hash",
                REFRESH_EXPIRY,
                DATABASE_REFRESH_EXPIRY
        ));
        when(jwtService.issueAccessToken(any(User.class)))
                .thenReturn(new IssuedAccessToken("access-token", ACCESS_EXPIRY));
    }

    private User user(Long id, UserStatus status) {
        User user = new User("Staff User", "staff@example.test", "bcrypt-hash");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", Role.STAFF);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }
}

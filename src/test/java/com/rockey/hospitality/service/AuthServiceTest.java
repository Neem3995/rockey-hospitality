package com.rockey.hospitality.service;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.exception.ApiException.*;
import com.rockey.hospitality.repository.UserRepository;
import com.rockey.hospitality.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder passwords;
    @Mock JwtService jwt;
    @Mock RefreshTokenService refresh;
    @Mock AuthenticationRateLimiter limiter;
    @Mock JwtService.IssuedAccessToken access;
    @Mock RefreshTokenService.IssuedRefreshToken refreshValue;
    AuthService service;
    User user;
    @BeforeEach void setup() {
        service = new AuthService(users, passwords, jwt, refresh, limiter);
        user = HousekeepingFixtures.user(3, User.Role.USER);
        lenient().when(users.save(any())).thenAnswer(i -> i.getArgument(0));
    }
    void session() {
        when(jwt.issueAccessToken(any())).thenReturn(access);
        when(refresh.issueRefreshToken()).thenReturn(refreshValue);
        when(access.getValue()).thenReturn("test-access");
        when(refreshValue.getHash()).thenReturn("test-refresh-hash");
        when(refreshValue.getRawValue()).thenReturn("test-refresh-cookie");
        when(refreshValue.getDatabaseExpiresAt()).thenReturn(LocalDateTime.now().plusDays(7));
    }
    @Test void publicRegistrationCreatesUserAndNormalizesEmail() {
        session(); when(passwords.encode("test-password")).thenReturn("test-only-hash");
        var result = service.register(" Test worker ", " WORKER@EXAMPLE.TEST ", "test-password", "test-ip");
        assertEquals(User.Role.USER, result.getResponse().getUser().getRole());
        assertEquals("worker@example.test", result.getResponse().getUser().getEmail());
        assertTrue(result.getResponse().getUser().getActive());
        verify(limiter).consumeRegistrationAttempt("test-ip");
    }
    @Test void duplicateRegistrationConflicts() {
        when(users.existsByEmailIgnoreCase("worker@example.test")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.register("Test worker", "worker@example.test", "test-password", "ip"));
    }
    @Test void bcryptByteLimitRejectsMultibytePassword() {
        String value = "é".repeat(40);
        assertThrows(BadRequestException.class, () -> service.hashPassword(value));
    }
    void lockedCandidate() {
        when(users.findIdByEmail("worker3@example.test")).thenReturn(Optional.of(3L));
        when(users.findForUpdate(3L)).thenReturn(Optional.of(user));
    }
    void lockedRefresh(String hash) {
        when(users.findIdByRefreshTokenHash(hash)).thenReturn(Optional.of(3L));
        when(users.findForUpdate(3L)).thenReturn(Optional.of(user));
    }
    @Test void loginChecksPasswordAndRotatesSession() {
        session(); lockedCandidate();
        when(passwords.matches("test-password", user.getPasswordHash())).thenReturn(true);
        assertNotNull(service.login("worker3@example.test", "test-password", "ip").getResponse());
        verify(limiter).resetLoginFailures("ip", "worker3@example.test");
        InOrder order = inOrder(users, passwords);
        order.verify(users).findIdByEmail("worker3@example.test");
        order.verify(users).findForUpdate(3L);
        order.verify(passwords).matches("test-password", user.getPasswordHash());
        order.verify(users).save(user);
        verify(users, never()).findByEmailIgnoreCase(anyString());
    }
    @Test void absentLoginFailsGenerically() {
        assertThrows(InvalidCredentialsException.class, () -> service.login("missing@example.test", "test-password", "ip"));
        verify(limiter).recordLoginFailure("ip", "missing@example.test");
        verify(users, never()).findForUpdate(anyLong());
    }
    @Test void loginFailsGenericallyWhenCandidateDisappearsBeforeLock() {
        when(users.findIdByEmail("worker3@example.test")).thenReturn(Optional.of(3L));
        assertThrows(InvalidCredentialsException.class, () -> service.login("worker3@example.test", "test-password", "ip"));
        verify(limiter).recordLoginFailure("ip", "worker3@example.test");
        verifyNoInteractions(passwords); verify(users, never()).save(any());
    }
    @Test void loginRechecksEmailOnLockedAccount() {
        lockedCandidate(); user.update("Test worker", "changed@example.test", User.Role.USER, true);
        assertThrows(InvalidCredentialsException.class, () -> service.login("worker3@example.test", "test-password", "ip"));
        verify(limiter).recordLoginFailure("ip", "worker3@example.test");
        verifyNoInteractions(passwords); verify(users, never()).save(any());
    }
    @Test void wrongPasswordFails() {
        lockedCandidate();
        assertThrows(InvalidCredentialsException.class, () -> service.login("worker3@example.test", "wrong", "ip"));
    }
    @Test void inactiveLoginFails() {
        user.deactivate(); lockedCandidate();
        assertThrows(InvalidCredentialsException.class, () -> service.login("worker3@example.test", "test-password", "ip"));
    }
    @Test void refreshRotatesOnlyValidSession() {
        session(); user.replaceRefreshSession("old", LocalDateTime.now().plusDays(1));
        when(refresh.hash("old-cookie")).thenReturn("old"); lockedRefresh("old");
        when(refresh.currentDatabaseTime()).thenReturn(LocalDateTime.now());
        service.refresh("old-cookie", "ip"); assertEquals("test-refresh-hash", user.getRefreshTokenHash());
        InOrder order = inOrder(users);
        order.verify(users).findIdByRefreshTokenHash("old"); order.verify(users).findForUpdate(3L); order.verify(users).save(user);
    }
    @Test void nullRefreshRejected() { assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(null, "ip")); }
    @Test void blankRefreshRejected() { assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(" ", "ip")); }
    @Test void unknownRefreshRejected() {
        when(refresh.hash("unknown")).thenReturn("unknown-hash");
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("unknown", "ip"));
        verify(users, never()).findForUpdate(anyLong());
    }
    @Test void refreshRejectedWhenCandidateDisappearsBeforeLock() {
        when(refresh.hash("cookie")).thenReturn("old"); when(users.findIdByRefreshTokenHash("old")).thenReturn(Optional.of(3L));
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("cookie", "ip"));
        verify(users, never()).save(any()); verify(refresh, never()).issueRefreshToken();
    }
    @Test void refreshRechecksHashOnLockedAccount() {
        user.replaceRefreshSession("rotated", LocalDateTime.now().plusDays(1));
        when(refresh.hash("cookie")).thenReturn("old"); lockedRefresh("old");
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("cookie", "ip"));
        assertEquals("rotated", user.getRefreshTokenHash());
        verify(users, never()).save(any()); verify(refresh, never()).issueRefreshToken();
    }
    @Test void refreshRejectedAfterLogoutRevokedLockedSession() {
        when(refresh.hash("cookie")).thenReturn("old"); lockedRefresh("old");
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("cookie", "ip"));
        verify(users, never()).save(any());
    }
    @Test void expiredRefreshRejected() {
        user.replaceRefreshSession("old", LocalDateTime.now().minusDays(1));
        when(refresh.hash("cookie")).thenReturn("old"); lockedRefresh("old");
        when(refresh.currentDatabaseTime()).thenReturn(LocalDateTime.now());
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("cookie", "ip"));
    }
    @Test void inactiveRefreshRejected() {
        user.replaceRefreshSession("old", LocalDateTime.now().plusDays(1)); user.deactivate();
        when(refresh.hash("cookie")).thenReturn("old"); lockedRefresh("old");
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("cookie", "ip"));
    }
    @Test void missingExpirationRejected() {
        user.replaceRefreshSession("old", null);
        when(refresh.hash("cookie")).thenReturn("old"); lockedRefresh("old");
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("cookie", "ip"));
    }
    @Test void profileIsSafe() {
        when(users.findById(3L)).thenReturn(Optional.of(user));
        assertEquals("Test worker", service.getCurrentUser(3L).getName());
    }
    @Test void profileMissing404() { assertThrows(ResourceNotFoundException.class, () -> service.getCurrentUser(99L)); }
    @Test void profileInactive401() {
        user.deactivate(); when(users.findById(3L)).thenReturn(Optional.of(user));
        assertThrows(InvalidCredentialsException.class, () -> service.getCurrentUser(3L));
    }
    @Test void logoutClearsBothFieldsAndIsIdempotent() {
        user.replaceRefreshSession("test-hash", LocalDateTime.now()); when(users.findForUpdate(3L)).thenReturn(Optional.of(user));
        service.logout(3L); service.logout(3L);
        assertNull(user.getRefreshTokenHash()); assertNull(user.getRefreshTokenExpiresAt());
        verify(users, times(2)).findForUpdate(3L);
        verify(users, never()).findById(anyLong());
    }
    @Test void logoutMissing404() { assertThrows(ResourceNotFoundException.class, () -> service.logout(99L)); }
}

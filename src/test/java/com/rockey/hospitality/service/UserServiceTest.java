package com.rockey.hospitality.service;
import com.rockey.hospitality.dto.UserDtos.*;
import com.rockey.hospitality.entity.*;
import com.rockey.hospitality.exception.ApiException.*;
import com.rockey.hospitality.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository users; @Mock TaskRepository tasks; @Mock AuthService auth;
    UserService service; User admin, manager, worker;
    @BeforeEach void setup() {
        service = new UserService(users, tasks, auth);
        admin = HousekeepingFixtures.user(1, User.Role.ADMIN); manager = HousekeepingFixtures.user(2, User.Role.MANAGER);
        worker = HousekeepingFixtures.user(3, User.Role.USER);
        lenient().when(users.findById(1L)).thenReturn(Optional.of(admin));
        lenient().when(users.findById(2L)).thenReturn(Optional.of(manager));
        lenient().when(users.findById(3L)).thenReturn(Optional.of(worker));
        lenient().when(users.findForUpdate(3L)).thenReturn(Optional.of(worker));
        lenient().when(users.save(any())).thenAnswer(i -> i.getArgument(0));
    }
    @Test void adminListsAll() { when(users.findAllByOrderByNameAsc()).thenReturn(List.of(admin, manager, worker)); assertEquals(3, service.list(1L).size()); }
    @Test void managerListsOnlyWorkers() { when(users.findByRoleOrderByNameAsc(User.Role.USER)).thenReturn(List.of(worker)); assertEquals(1, service.list(2L).size()); verify(users, never()).findAllByOrderByNameAsc(); }
    @Test void managerReadsWorker() { assertEquals(3L, service.get(2L, 3L).getId()); }
    @Test void managerCannotReadAdmin() { assertThrows(ForbiddenException.class, () -> service.get(2L, 1L)); }
    @Test void workerCannotListTeam() { assertThrows(ForbiddenException.class, () -> service.list(3L)); }
    @Test void inactiveSupervisorRejected() { manager.deactivate(); assertThrows(ForbiddenException.class, () -> service.list(2L)); }
    @Test void missingAccount404() { assertThrows(ResourceNotFoundException.class, () -> service.account(99L)); }
    @Test void adminCreatesManager() {
        when(auth.hashPassword("test-password")).thenReturn("test-hash");
        var request = new CreateUserRequest(" New manager ", " MANAGER@EXAMPLE.TEST ", "test-password", User.Role.MANAGER);
        assertEquals(User.Role.MANAGER, service.create(1L, request).getRole());
    }
    @Test void managerCreatesWorker() {
        when(auth.hashPassword("test-password")).thenReturn("test-hash");
        assertEquals(User.Role.USER, service.create(2L, new CreateUserRequest("Worker", "worker@example.test", "test-password", User.Role.USER)).getRole());
    }
    @Test void managerCannotCreateManager() {
        var request = new CreateUserRequest("Manager", "manager@example.test", "test-password", User.Role.MANAGER);
        assertThrows(ForbiddenException.class, () -> service.create(2L, request));
    }
    @Test void nobodyCreatesAdminThroughApi() {
        var request = new CreateUserRequest("Admin", "admin@example.test", "test-password", User.Role.ADMIN);
        assertThrows(ForbiddenException.class, () -> service.create(1L, request));
    }
    @Test void duplicateEmail409() {
        when(users.existsByEmailIgnoreCase("worker@example.test")).thenReturn(true);
        var request = new CreateUserRequest("Worker", "worker@example.test", "test-password", User.Role.USER);
        assertThrows(ConflictException.class, () -> service.create(1L, request));
    }
    @Test void updatePromotesWorkerAndRevokesRefresh() {
        worker.replaceRefreshSession("test-hash", java.time.LocalDateTime.now());
        var response = service.update(1L, 3L, new UpdateUserRequest("Supervisor", "worker3@example.test", User.Role.MANAGER, true));
        assertEquals(User.Role.MANAGER, response.getRole()); assertNull(worker.getRefreshTokenHash());
    }
    @Test void managerCannotUpdate() {
        var request = new UpdateUserRequest("Worker", "worker3@example.test", User.Role.USER, true);
        assertThrows(ForbiddenException.class, () -> service.update(2L, 3L, request));
    }
    @Test void cannotPromoteToAdmin() {
        var request = new UpdateUserRequest("Admin", "worker3@example.test", User.Role.ADMIN, true);
        assertThrows(ForbiddenException.class, () -> service.update(1L, 3L, request));
    }
    @Test void cannotChangeBootstrapAdmin() {
        when(users.findForUpdate(1L)).thenReturn(Optional.of(admin));
        var request = new UpdateUserRequest("Admin", "admin@example.test", User.Role.ADMIN, true);
        assertThrows(ForbiddenException.class, () -> service.update(1L, 1L, request));
        assertThrows(ForbiddenException.class, () -> service.deactivate(1L, 1L));
    }
    @Test void updateDuplicateEmail409() {
        when(users.existsByEmailIgnoreCaseAndIdNot("other@example.test", 3L)).thenReturn(true);
        var request = new UpdateUserRequest("Worker", "other@example.test", User.Role.USER, true);
        assertThrows(ConflictException.class, () -> service.update(1L, 3L, request));
    }
    @Test void deactivationWithWork409() {
        when(tasks.findByAssignedUserIdAndStatusIn(eq(3L), any())).thenReturn(List.of(HousekeepingFixtures.task(1, worker, HousekeepingFixtures.room(1,Room.Status.DIRTY))));
        assertThrows(ConflictException.class, () -> service.deactivate(1L, 3L));
        var request = new UpdateUserRequest("Worker", "worker3@example.test", User.Role.USER, false);
        assertThrows(ConflictException.class, () -> service.update(1L, 3L, request));
        assertTrue(worker.isActive());
    }
    @Test void deactivationKeepsRowAndRevokesSession() {
        worker.replaceRefreshSession("test-hash", java.time.LocalDateTime.now());
        service.deactivate(1L, 3L); assertFalse(worker.isActive()); assertNull(worker.getRefreshTokenExpiresAt()); verify(users).save(worker);
    }
    @Test void missingUpdate404() {
        var request = new UpdateUserRequest("Worker", "worker@example.test", User.Role.USER, true);
        assertThrows(ResourceNotFoundException.class, () -> service.update(1L, 99L, request));
    }
}

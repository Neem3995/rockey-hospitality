package com.rockey.hospitality.hardening;

import com.rockey.hospitality.dto.RoomDtos.*;
import com.rockey.hospitality.dto.TaskDtos.*;
import com.rockey.hospitality.dto.UserDtos.*;
import com.rockey.hospitality.entity.*;
import com.rockey.hospitality.exception.ApiException.*;
import com.rockey.hospitality.repository.*;
import com.rockey.hospitality.service.*;
import com.rockey.hospitality.security.RefreshTokenService;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Opt-in persisted workflow/rollback/race checks; only the disposable hardening schema may be used. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named="ROCKEY_HARDENING_LIVE", matches="true")
class HousekeepingMySqlTest {
    @Autowired UserRepository accounts;
    @Autowired RoomRepository roomRows;
    @Autowired TaskRepository taskRows;
    @Autowired InspectionRepository inspectionRows;
    @Autowired RoomService rooms;
    @Autowired TaskService tasks;
    @Autowired UserService users;
    @Autowired AuthService auth;
    @Autowired RefreshTokenService refreshTokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean PasswordEncoder passwords;
    Long adminId, managerId, workerId, otherId, roomId;

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        String url = System.getenv("ROCKEY_DB_URL");
        if (url == null || !url.startsWith("jdbc:mysql://127.0.0.1:3306/rockey_hospitality_hardening?")) throw new IllegalStateException("Disposable MySQL URL required.");
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        registry.add("rockey.security.jwt-secret", () -> Base64.getEncoder().encodeToString(key));
        registry.add("spring.datasource.url", () -> url);
    }
    @BeforeEach
    void fixtures() {
        assertEquals("rockey_hospitality_hardening", jdbc.queryForObject("SELECT DATABASE()",String.class));
        jdbc.update("DELETE FROM inspections"); jdbc.update("DELETE FROM tasks"); jdbc.update("DELETE FROM rooms"); jdbc.update("DELETE FROM users");
        adminId = accounts.save(new User("Test admin","admin@example.test",auth.hashPassword("test-fixture-password"),User.Role.ADMIN)).getId();
        managerId = accounts.save(new User("Test manager","manager@example.test",auth.hashPassword("test-fixture-password"),User.Role.MANAGER)).getId();
        workerId = accounts.save(new User("Test worker","worker@example.test",auth.hashPassword("test-fixture-password"))).getId();
        otherId = accounts.save(new User("Other worker","other@example.test",auth.hashPassword("test-fixture-password"))).getId();
        roomId = rooms.create(managerId,new RoomRequest("101",1,Room.Status.DIRTY,true)).getId();
    }
    TaskRequest input(Long owner) { return new TaskRequest("Clean room","Routine clean",Task.Priority.MEDIUM,owner,roomId,LocalDateTime.now().plusHours(1)); }
    Long completeTask() {
        Long id = tasks.create(managerId,input(workerId)).getId();
        tasks.status(workerId,id,Task.Status.IN_PROGRESS); tasks.status(workerId,id,Task.Status.COMPLETED); return id;
    }
    @Test void exactlyFourTablesAndForeignKeysExist() {
        assertEquals(Set.of("users","rooms","tasks","inspections"), new HashSet<>(jdbc.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()",String.class)));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()",Integer.class));
    }
    @Test void passingInspectionPersistsWorkflowAndHistory() {
        Long id = completeTask();
        assertEquals(Room.Status.INSPECTION,rooms.get(managerId,roomId).getStatus());
        InspectionResponse result = rooms.inspect(managerId,roomId,new InspectionRequest(id,Inspection.Result.PASS,"Checked"));
        assertNotNull(result.getId()); assertEquals(managerId,result.getInspectedBy().getId());
        assertEquals(Room.Status.READY,rooms.get(managerId,roomId).getStatus());
        assertEquals(Task.Status.COMPLETED,tasks.get(workerId,id).getStatus());
        assertNotNull(tasks.get(workerId,id).getCompletedAt());
        assertEquals(1,rooms.inspectionHistory(managerId,roomId).size());
    }
    @Test void failingInspectionAllowsAnotherCleanAndPreservesBothHistories() {
        Long first = completeTask();
        rooms.inspect(adminId,roomId,new InspectionRequest(first,Inspection.Result.FAIL,"Rework"));
        assertEquals(Room.Status.DIRTY,rooms.get(managerId,roomId).getStatus());
        Long second = completeTask(); rooms.inspect(managerId,roomId,new InspectionRequest(second,Inspection.Result.PASS,"Corrected"));
        assertEquals(2,rooms.inspectionHistory(managerId,roomId).size()); assertEquals(2,taskRows.count());
        assertEquals(Room.Status.READY,rooms.get(managerId,roomId).getStatus());
    }
    @Test void userCannotReadOrModifyAnotherWorkersTask() {
        Long id = tasks.create(managerId,input(otherId)).getId();
        assertTrue(tasks.list(workerId).isEmpty()); assertEquals(1,tasks.list(otherId).size());
        assertThrows(ForbiddenException.class, () -> tasks.get(workerId,id));
        assertThrows(ForbiddenException.class, () -> tasks.status(workerId,id,Task.Status.IN_PROGRESS));
        assertEquals(Task.Status.ASSIGNED,tasks.get(otherId,id).getStatus());
        tasks.status(otherId,id,Task.Status.IN_PROGRESS);
        assertThrows(ForbiddenException.class, () -> tasks.status(workerId,id,Task.Status.COMPLETED));
        assertEquals(Task.Status.IN_PROGRESS,tasks.get(otherId,id).getStatus());
        assertEquals(Room.Status.CLEANING,rooms.get(managerId,roomId).getStatus());
    }
    @ParameterizedTest
    @EnumSource(value = User.Role.class, names = {"MANAGER", "ADMIN"})
    void supervisorsManageAndInspectButCannotExecute(User.Role role) {
        Long actor = role == User.Role.MANAGER ? managerId : adminId;
        Long id = tasks.create(actor,input(workerId)).getId();
        assertThrows(ForbiddenException.class, () -> tasks.status(actor,id,Task.Status.IN_PROGRESS));
        assertEquals(Task.Status.ASSIGNED,tasks.get(workerId,id).getStatus());
        assertEquals(Room.Status.DIRTY,rooms.get(actor,roomId).getStatus());
        tasks.status(workerId,id,Task.Status.IN_PROGRESS);
        assertThrows(ForbiddenException.class, () -> tasks.status(actor,id,Task.Status.COMPLETED));
        assertEquals(Task.Status.IN_PROGRESS,tasks.get(workerId,id).getStatus());
        assertEquals(Room.Status.CLEANING,rooms.get(actor,roomId).getStatus());
        tasks.status(workerId,id,Task.Status.COMPLETED);
        rooms.inspect(actor,roomId,new InspectionRequest(id,Inspection.Result.PASS,"Checked"));
        assertEquals(Room.Status.READY,rooms.get(actor,roomId).getStatus());
        rooms.status(actor,roomId,Room.Status.DIRTY);
        Long rework = tasks.create(actor,input(workerId)).getId();
        assertEquals(otherId,tasks.update(actor,rework,input(otherId)).getAssignedUser().getId());
        assertThrows(ForbiddenException.class, () -> tasks.status(workerId,rework,Task.Status.IN_PROGRESS));
        assertEquals(Task.Status.CANCELLED,tasks.status(actor,rework,Task.Status.CANCELLED).getStatus());
        Long cancelled = tasks.create(actor,input(workerId)).getId(); tasks.cancel(actor,cancelled);
        assertEquals(Task.Status.CANCELLED,tasks.get(actor,cancelled).getStatus());
        assertEquals(1,rooms.inspectionHistory(actor,roomId).size()); assertEquals(3,taskRows.count());
    }
    @Test void deactivationRoleAndRoomGuardsPersist() {
        Long id = tasks.create(managerId,input(workerId)).getId();
        assertThrows(ConflictException.class, () -> users.deactivate(adminId,workerId));
        assertThrows(ConflictException.class, () -> rooms.deactivate(managerId,roomId));
        tasks.cancel(managerId,id); rooms.deactivate(managerId,roomId); users.deactivate(adminId,workerId);
        assertFalse(accounts.findById(workerId).orElseThrow().isActive());
        assertFalse(rooms.get(managerId,roomId).getActive()); assertEquals(Task.Status.CANCELLED,tasks.get(managerId,id).getStatus());
        assertEquals(1,taskRows.count());
    }
    @Test void invalidInspectionCannotPartiallyChangeRoomOrHistory() {
        Long id = completeTask();
        assertThrows(ResourceNotFoundException.class, () -> rooms.inspect(managerId,roomId,new InspectionRequest(Long.MAX_VALUE,Inspection.Result.PASS,null)));
        assertEquals(Room.Status.INSPECTION,rooms.get(managerId,roomId).getStatus()); assertEquals(0,inspectionRows.count());
        // Direct service call intentionally exceeds DB length to prove transaction rollback, not DTO validation.
        InspectionRequest oversized = new InspectionRequest(id,Inspection.Result.PASS,"x".repeat(1001));
        assertThrows(RuntimeException.class, () -> rooms.inspect(managerId,roomId,oversized));
        assertEquals(Room.Status.INSPECTION,rooms.get(managerId,roomId).getStatus()); assertEquals(0,inspectionRows.count());
    }
    @Test void concurrentTaskCreationSerializesOnRoom() throws Exception {
        List<Object> results = race(() -> tasks.create(managerId,input(workerId)), () -> tasks.create(managerId,input(otherId)));
        assertEquals(1,results.stream().filter(TaskResponse.class::isInstance).count());
        assertEquals(1,results.stream().filter(ConflictException.class::isInstance).count()); assertEquals(1,taskRows.count());
    }
    @Test void concurrentInspectionCreatesOneHistoryEntry() throws Exception {
        Long id = completeTask(); InspectionRequest input = new InspectionRequest(id,Inspection.Result.PASS,null);
        List<Object> results = race(() -> rooms.inspect(managerId,roomId,input), () -> rooms.inspect(adminId,roomId,input));
        assertEquals(1,results.stream().filter(InspectionResponse.class::isInstance).count());
        assertEquals(1,results.stream().filter(ConflictException.class::isInstance).count()); assertEquals(1,inspectionRows.count());
    }
    @Test void refreshRotationRaceAcceptsOneAndLogoutRevokesTheWinner() throws Exception {
        AuthService.AuthSession session = auth.login("worker@example.test","test-fixture-password","live-login");
        String refresh = session.getRawRefreshToken();
        List<Object> results = race(() -> auth.refresh(refresh,"live-a"), () -> auth.refresh(refresh,"live-b"));
        assertEquals(1,results.stream().filter(AuthService.AuthSession.class::isInstance).count());
        assertEquals(1,results.stream().filter(InvalidRefreshTokenException.class::isInstance).count());
        AuthService.AuthSession winner = (AuthService.AuthSession) results.stream().filter(AuthService.AuthSession.class::isInstance).findFirst().orElseThrow();
        String rotated = winner.getRawRefreshToken();
        assertNotEquals(refresh,rotated); auth.logout(workerId); auth.logout(workerId);
        assertThrows(InvalidRefreshTokenException.class, () -> auth.refresh(rotated,"live-c"));
        User user = accounts.findById(workerId).orElseThrow(); assertNull(user.getRefreshTokenHash()); assertNull(user.getRefreshTokenExpiresAt());
    }
    @Test void managerCannotProvisionPrivilegedAccountAndAdminCanProvisionManager() {
        CreateUserRequest privileged = new CreateUserRequest("New manager","new@example.test","test-fixture-password",User.Role.MANAGER);
        assertThrows(ForbiddenException.class, () -> users.create(managerId,privileged));
        assertEquals(User.Role.MANAGER,users.create(adminId,privileged).getRole());
        CreateUserRequest admin = new CreateUserRequest("Extra admin","extra@example.test","test-fixture-password",User.Role.ADMIN);
        assertThrows(ForbiddenException.class, () -> users.create(adminId,admin));
    }
    @Test void assignmentAndWorkerDeactivationCannotBothSucceed() throws Exception {
        List<Object> results = race(() -> tasks.create(managerId,input(workerId)), () -> { users.deactivate(adminId,workerId); return "deactivated"; });
        assertEquals(1,results.stream().filter(ConflictException.class::isInstance).count());
        User worker = accounts.findById(workerId).orElseThrow();
        assertTrue(worker.isActive() || taskRows.count() == 0);
    }
    @Test void taskCreationAndRoomDeactivationCannotBothSucceed() throws Exception {
        List<Object> results = race(() -> tasks.create(managerId,input(workerId)), () -> { rooms.deactivate(managerId,roomId); return "deactivated"; });
        assertEquals(1,results.stream().filter(ConflictException.class::isInstance).count());
        assertTrue(rooms.get(managerId,roomId).getActive() || taskRows.count() == 0);
    }
    @Test
    void loginThenDeactivationCannotResurrectAccount() throws Exception {
        List<Object> results = orderedAccountRace(
                () -> auth.login("worker@example.test", "test-fixture-password", "login-deactivate"),
                () -> { users.deactivate(adminId, workerId); return "deactivated"; }, true);
        assertInstanceOf(AuthService.AuthSession.class, results.get(0));
        assertEquals("deactivated", results.get(1));
        User worker = accounts.findById(workerId).orElseThrow();
        assertFalse(worker.isActive());
        assertNull(worker.getRefreshTokenHash());
        assertThrows(InvalidCredentialsException.class,
                () -> auth.login("worker@example.test", "test-fixture-password", "inactive-login"));
    }

    @Test
    void loginThenIdentityChangePreservesAdministrativeValues() throws Exception {
        List<Object> results = orderedAccountRace(
                () -> auth.login("worker@example.test", "test-fixture-password", "login-update"),
                () -> users.update(adminId, workerId,
                        new UpdateUserRequest("Changed worker", "changed@example.test", User.Role.MANAGER, true)), true);
        assertInstanceOf(AuthService.AuthSession.class, results.get(0));
        assertInstanceOf(UserResponse.class, results.get(1));
        User worker = accounts.findById(workerId).orElseThrow();
        assertEquals("Changed worker", worker.getName());
        assertEquals("changed@example.test", worker.getEmail());
        assertEquals(User.Role.MANAGER, worker.getRole());
        assertNull(worker.getRefreshTokenHash());
    }

    @Test
    void deactivationThenLoginReadsCommittedInactiveState() throws Exception {
        List<Object> results = orderedAccountRace(
                () -> { users.deactivate(adminId, workerId); return "deactivated"; },
                () -> auth.login("worker@example.test", "test-fixture-password", "deactivate-login"), false);
        assertEquals("deactivated", results.get(0));
        assertInstanceOf(InvalidCredentialsException.class, results.get(1));
        User worker = accounts.findById(workerId).orElseThrow();
        assertFalse(worker.isActive());
        assertNull(worker.getRefreshTokenHash());
    }

    @Test
    void identityChangeThenLoginCannotUseOldEmailOrRole() throws Exception {
        List<Object> results = orderedAccountRace(
                () -> users.update(adminId, workerId,
                        new UpdateUserRequest("Changed worker", "changed@example.test", User.Role.MANAGER, true)),
                () -> auth.login("worker@example.test", "test-fixture-password", "update-login"), false);
        assertInstanceOf(UserResponse.class, results.get(0));
        assertInstanceOf(InvalidCredentialsException.class, results.get(1));
        var session = auth.login("changed@example.test", "test-fixture-password", "current-email-login");
        assertEquals(User.Role.MANAGER, session.getResponse().getUser().getRole());
        assertEquals("changed@example.test", session.getResponse().getUser().getEmail());
    }

    @Test
    void loginThenRefreshRejectsPreviousSession() throws Exception {
        String oldCookie = auth.login("worker@example.test", "test-fixture-password", "initial-session")
                .getRawRefreshToken();
        List<Object> results = orderedAccountRace(
                () -> auth.login("worker@example.test", "test-fixture-password", "replace-session"),
                () -> auth.refresh(oldCookie, "old-session-refresh"), true);
        var winner = assertInstanceOf(AuthService.AuthSession.class, results.get(0));
        assertInstanceOf(InvalidRefreshTokenException.class, results.get(1));
        assertCurrentSession(winner);
    }

    @Test
    void refreshThenLoginRetainsOnlyLastSession() throws Exception {
        String oldCookie = auth.login("worker@example.test", "test-fixture-password", "initial-refresh")
                .getRawRefreshToken();
        List<Object> results = orderedAccountRace(
                () -> auth.refresh(oldCookie, "rotate-session"),
                () -> auth.login("worker@example.test", "test-fixture-password", "login-after-refresh"), false);
        var rotated = assertInstanceOf(AuthService.AuthSession.class, results.get(0));
        var winner = assertInstanceOf(AuthService.AuthSession.class, results.get(1));
        assertCurrentSession(winner);
        assertThrows(InvalidRefreshTokenException.class,
                () -> auth.refresh(rotated.getRawRefreshToken(), "superseded-refresh"));
    }

    @Test
    void loginThenLogoutRevokesNewRefreshSession() throws Exception {
        List<Object> results = orderedAccountRace(
                () -> auth.login("worker@example.test", "test-fixture-password", "login-before-logout"),
                () -> { auth.logout(workerId); return "logged-out"; }, true);
        var session = assertInstanceOf(AuthService.AuthSession.class, results.get(0));
        assertEquals("logged-out", results.get(1));
        User worker = accounts.findById(workerId).orElseThrow();
        assertTrue(worker.isActive());
        assertNull(worker.getRefreshTokenHash());
        assertNull(worker.getRefreshTokenExpiresAt());
        assertThrows(InvalidRefreshTokenException.class,
                () -> auth.refresh(session.getRawRefreshToken(), "logged-out-refresh"));
    }

    @Test
    void logoutThenLoginCreatesNewRefreshSession() throws Exception {
        auth.login("worker@example.test", "test-fixture-password", "before-logout");
        List<Object> results = orderedAccountRace(
                () -> { auth.logout(workerId); return "logged-out"; },
                () -> auth.login("worker@example.test", "test-fixture-password", "after-logout"), false);
        assertEquals("logged-out", results.get(0));
        assertCurrentSession(assertInstanceOf(AuthService.AuthSession.class, results.get(1)));
    }

    // Persisted pagination: real service page methods and repository queries; no mocked paging.
    @Test void taskPagesScopeRowsAndTotalsToTheAuthenticatedWorker() {
        Long room102 = rooms.create(managerId,new RoomRequest("102",1,Room.Status.DIRTY,true)).getId();
        Long room103 = rooms.create(managerId,new RoomRequest("103",1,Room.Status.DIRTY,true)).getId();
        Long room104 = rooms.create(managerId,new RoomRequest("104",1,Room.Status.DIRTY,true)).getId();
        Long completed = completeTask();
        Long assigned = tasks.create(managerId,taskIn(room102,workerId)).getId();
        Long cancelled = tasks.create(managerId,taskIn(room103,workerId)).getId(); tasks.cancel(managerId,cancelled);
        Long reassigned = tasks.create(managerId,taskIn(room103,workerId)).getId();
        Long othersTask = tasks.create(managerId,taskIn(room104,otherId)).getId();

        var first = tasks.page(workerId,0,2,null); var second = tasks.page(workerId,1,2,null);
        assertEquals(4,first.totalElements()); assertEquals(2,first.totalPages()); assertEquals(4,second.totalElements());
        assertEquals(List.of(reassigned,cancelled),taskIds(first)); assertEquals(List.of(assigned,completed),taskIds(second));
        assertTrue(first.items().stream().allMatch(t -> t.getAssignedUser().getId().equals(workerId)));
        var assignedOnly = tasks.page(workerId,0,20,Task.Status.ASSIGNED);
        assertEquals(List.of(reassigned,assigned),taskIds(assignedOnly)); assertEquals(2,assignedOnly.totalElements());
        assertEquals(1,tasks.page(workerId,0,20,Task.Status.CANCELLED).totalElements());
        var beyond = tasks.page(workerId,5,2,null);
        assertTrue(beyond.items().isEmpty()); assertEquals(5,beyond.page()); assertEquals(4,beyond.totalElements()); assertEquals(2,beyond.totalPages());
        assertEquals(List.of(othersTask),taskIds(tasks.page(otherId,0,20,null)));

        List<Long> all = List.of(othersTask,reassigned,cancelled,assigned,completed);
        assertEquals(all,taskIds(tasks.page(managerId,0,20,null))); assertEquals(all,taskIds(tasks.page(adminId,0,20,null)));
        assertEquals(List.of(othersTask,reassigned,assigned),taskIds(tasks.page(managerId,0,20,Task.Status.ASSIGNED)));
        assertEquals(5,tasks.page(managerId,2,2,null).totalElements());
    }
    @Test void accountPagesLimitManagersToUserAccountsWithStableNameTieBreak() {
        Long twin = users.create(adminId,new CreateUserRequest("Test worker","twin@example.test","test-fixture-password",User.Role.USER)).getId();
        var managed = users.page(managerId,0,20);
        assertEquals(List.of(otherId,workerId,twin),userIds(managed)); assertEquals(3,managed.totalElements());
        assertTrue(managed.items().stream().allMatch(u -> u.getRole() == User.Role.USER));
        var beyond = users.page(managerId,3,2);
        assertTrue(beyond.items().isEmpty()); assertEquals(3,beyond.totalElements()); assertEquals(2,beyond.totalPages());
        assertEquals(List.of(otherId,adminId,managerId,workerId,twin),userIds(users.page(adminId,0,20)));
        var adminMiddle = users.page(adminId,1,2);
        assertEquals(List.of(managerId,workerId),userIds(adminMiddle)); assertEquals(5,adminMiddle.totalElements()); assertEquals(3,adminMiddle.totalPages());
        assertThrows(ForbiddenException.class, () -> users.page(workerId,0,20));
    }
    @Test void roomPagesUsePersistedRoomNumberOrderAndTotals() {
        rooms.create(managerId,new RoomRequest("103",1,Room.Status.READY,true));
        rooms.create(managerId,new RoomRequest("1001",10,Room.Status.READY,true));
        rooms.create(managerId,new RoomRequest("102",1,Room.Status.READY,true));
        var first = rooms.page(managerId,0,2); var second = rooms.page(adminId,1,2);
        assertEquals(List.of("1001","101"),roomNumbers(first)); assertEquals(List.of("102","103"),roomNumbers(second));
        assertEquals(4,first.totalElements()); assertEquals(2,first.totalPages()); assertEquals(4,second.totalElements());
        var beyond = rooms.page(managerId,9,2); assertTrue(beyond.items().isEmpty()); assertEquals(4,beyond.totalElements());
        assertThrows(ForbiddenException.class, () -> rooms.page(workerId,0,20));
    }
    @Test void inspectionHistoryPagesAcrossPassAndFailCycles() {
        Long firstTask = completeTask(); Long firstFail = rooms.inspect(adminId,roomId,new InspectionRequest(firstTask,Inspection.Result.FAIL,"Rework")).getId();
        Long secondTask = completeTask(); Long secondFail = rooms.inspect(managerId,roomId,new InspectionRequest(secondTask,Inspection.Result.FAIL,"Again")).getId();
        Long thirdTask = completeTask(); Long pass = rooms.inspect(managerId,roomId,new InspectionRequest(thirdTask,Inspection.Result.PASS,"Ready")).getId();
        Long otherRoom = rooms.create(managerId,new RoomRequest("102",1,Room.Status.READY,true)).getId();

        var newest = rooms.inspectionPage(managerId,roomId,0,2); var oldest = rooms.inspectionPage(adminId,roomId,1,2);
        assertEquals(List.of(pass,secondFail),newest.items().stream().map(InspectionResponse::getId).toList());
        assertEquals(List.of(Inspection.Result.PASS,Inspection.Result.FAIL),newest.items().stream().map(InspectionResponse::getResult).toList());
        assertEquals(List.of(firstFail),oldest.items().stream().map(InspectionResponse::getId).toList());
        assertEquals(3,newest.totalElements()); assertEquals(2,newest.totalPages()); assertEquals(3,oldest.totalElements());
        var beyond = rooms.inspectionPage(managerId,roomId,4,2); assertTrue(beyond.items().isEmpty()); assertEquals(3,beyond.totalElements());
        var empty = rooms.inspectionPage(managerId,otherRoom,0,20); assertEquals(0,empty.totalElements()); assertEquals(0,empty.totalPages());
        assertThrows(ResourceNotFoundException.class, () -> rooms.inspectionPage(managerId,Long.MAX_VALUE,0,20));
        assertThrows(ForbiddenException.class, () -> rooms.inspectionPage(workerId,roomId,0,20));
    }
    TaskRequest taskIn(Long room, Long owner) { return new TaskRequest("Clean room","Routine clean",Task.Priority.MEDIUM,owner,room,LocalDateTime.now().plusHours(1)); }
    static List<Long> taskIds(com.rockey.hospitality.dto.PageResponse<TaskResponse> page) { return page.items().stream().map(TaskResponse::getId).toList(); }
    static List<Long> userIds(com.rockey.hospitality.dto.PageResponse<UserResponse> page) { return page.items().stream().map(UserResponse::getId).toList(); }
    static List<String> roomNumbers(com.rockey.hospitality.dto.PageResponse<RoomResponse> page) { return page.items().stream().map(RoomResponse::getRoomNumber).toList(); }

    private void assertCurrentSession(AuthService.AuthSession session) {
        assertEquals(refreshTokens.hash(session.getRawRefreshToken()),
                accounts.findById(workerId).orElseThrow().getRefreshTokenHash());
    }

    /**
     * Holds the first real service transaction, then proves that the second connection waits for
     * its database lock before releasing it. No mocked repository saves or timing-only sleeps.
     * The live database account must be able to read performance_schema.data_lock_waits/threads.
     */
    private List<Object> orderedAccountRace(Callable<?> first, Callable<?> second, boolean pausePassword)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch firstReady = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicLong secondConnection = new AtomicLong();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(25);
        if (pausePassword) {
            doAnswer(invocation -> {
                firstReady.countDown();
                awaitLatch(releaseFirst);
                return invocation.callRealMethod();
            }).when(passwords).matches(eq("test-fixture-password"), anyString());
        }
        try {
            Future<Object> firstResult = pool.submit(() -> transactionResult(transaction, () -> {
                Object result = first.call();
                if (!pausePassword) {
                    firstReady.countDown();
                    awaitLatch(releaseFirst);
                }
                return result;
            }));
            assertTrue(firstReady.await(10, TimeUnit.SECONDS), "First transaction did not reach its gate.");
            Future<Object> secondResult = pool.submit(() -> transactionResult(transaction, () -> {
                secondConnection.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                secondStarted.countDown();
                return second.call();
            }));
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS), "Second transaction did not start.");
            awaitDatabaseLockWait(secondConnection.get(), secondResult);
            releaseFirst.countDown();
            return List.of(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "Transaction workers did not stop.");
            reset(passwords);
        }
    }

    private Object transactionResult(TransactionTemplate transaction, Callable<?> action) {
        try {
            return transaction.execute(status -> {
                try {
                    return action.call();
                } catch (RuntimeException failure) {
                    throw failure;
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    private void awaitDatabaseLockWait(long connection, Future<?> result) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waits = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM performance_schema.data_lock_waits w
                    JOIN performance_schema.threads t ON t.THREAD_ID = w.REQUESTING_THREAD_ID
                    WHERE t.PROCESSLIST_ID = ?
                    """, Integer.class, connection);
            if (waits != null && waits > 0) {
                return;
            }
            assertFalse(result.isDone(), "Competing service completed without waiting for the account lock.");
            Thread.yield();
        }
        fail("Competing service did not enter a database lock wait.");
    }

    private static void awaitLatch(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(20, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Transaction gate timed out.");
        }
    }

    private List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> action : List.of(first,second)) futures.add(pool.submit(() -> {
                ready.countDown(); if (!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Race start timed out.");
                try { return action.call(); } catch (RuntimeException failure) { return failure; }
            }));
            assertTrue(ready.await(10,TimeUnit.SECONDS)); start.countDown();
            return List.of(futures.get(0).get(30,TimeUnit.SECONDS),futures.get(1).get(30,TimeUnit.SECONDS));
        } finally { start.countDown(); pool.shutdownNow(); }
    }
}

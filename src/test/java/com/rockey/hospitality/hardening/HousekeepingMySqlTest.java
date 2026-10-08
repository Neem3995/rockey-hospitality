package com.rockey.hospitality.hardening;

import com.rockey.hospitality.dto.RoomDtos.*;
import com.rockey.hospitality.dto.TaskDtos.*;
import com.rockey.hospitality.dto.UserDtos.*;
import com.rockey.hospitality.entity.*;
import com.rockey.hospitality.exception.ApiException.*;
import com.rockey.hospitality.repository.*;
import com.rockey.hospitality.service.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import static org.junit.jupiter.api.Assertions.*;

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
    @Autowired JdbcTemplate jdbc;
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

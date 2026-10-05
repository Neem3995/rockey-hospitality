package com.rockey.hospitality.hardening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockey.hospitality.RockeyHospitalityApplication;
import com.rockey.hospitality.dto.employee.UpdateEmployeeRequest;
import com.rockey.hospitality.dto.inventory.CreateInventoryItemRequest;
import com.rockey.hospitality.dto.task.CreateTaskRequest;
import com.rockey.hospitality.entity.EmployeeStatus;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.exception.ConflictException;
import com.rockey.hospitality.exception.InvalidRefreshTokenException;
import com.rockey.hospitality.exception.InvalidCredentialsException;
import com.rockey.hospitality.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in destructive fixtures ONLY in the explicitly authorized disposable schema. */
@EnabledIfEnvironmentVariable(named = "ROCKEY_HARDENING_LIVE", matches = "true")
@SpringBootTest(classes = RockeyHospitalityApplication.class, properties = {
        "rockey.alerts.scan-delay-ms=3600000", "spring.jpa.show-sql=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HardeningMySqlTest {
    private static final AtomicLong IDS = new AtomicLong(20000);
    @Autowired JdbcTemplate sql;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder passwords;
    @Autowired TaskService tasks;
    @Autowired DepartmentService departments;
    @Autowired EmployeeService employees;
    @Autowired RoomService rooms;
    @Autowired InventoryService inventory;
    @Autowired RegistrationService registrations;
    @Autowired AlertAutomationService automation;
    @Autowired AnalyticsService analytics;
    @Autowired AuthService auth;
    private String fixturePassword;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String url = System.getenv("ROCKEY_DB_URL");
        if (url == null || !url.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):3306/rockey_hospitality_hardening(?:\\?.*)?")) {
            throw new IllegalStateException("Live hardening requires the approved disposable local schema.");
        }
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        registry.add("rockey.security.jwt-secret", () -> Base64.getEncoder().encodeToString(bytes));
    }

    @BeforeAll
    void disposableFixtures() throws Exception {
        assertEquals("rockey_hospitality_hardening", sql.queryForObject("SELECT DATABASE()", String.class));
        // FK-safe reset: no schema or table outside the approved connection is touched.
        for (String table : List.of("alerts", "event_registrations", "tasks", "inventory_items",
                "employees", "events", "rooms", "users", "departments")) sql.update("DELETE FROM " + table);
        sql.update("INSERT INTO departments(id,name) VALUES(10001,'Housekeeping'),(10002,'Engineering')");
        String temporary = UUID.randomUUID() + "!Aa7";
        fixturePassword = temporary;
        String hash = passwords.encode(temporary);
        assertTrue(passwords.matches(temporary, hash));
        for (long id : List.of(10001L, 10002L, 10003L, 10004L, 10005L, 10006L)) {
            String role = id == 10001 ? "ADMIN" : id == 10002 || id == 10004 || id == 10006 ? "STAFF" : "USER";
            Long department = role.equals("USER") ? null : id == 10004 ? 10002L : 10001L;
            sql.update("INSERT INTO users(id,name,email,password_hash,role,department_id) VALUES(?,?,?,?,?,?)",
                    id, "Hardening Test " + id, "hardening" + id + "@example.test", hash, role, department);
        }
        sql.update("INSERT INTO employees(id,user_id,name,email,department_id,job_role) VALUES"
                + "(10001,10001,'Hardening ADMIN','employee10001@example.test',10001,'Administrator'),"
                + "(10002,10002,'Hardening STAFF','employee10002@example.test',10001,'Housekeeper'),"
                + "(10004,10004,'Other STAFF','employee10004@example.test',10002,'Engineer')");
        sql.update("INSERT INTO rooms(id,room_number,floor,status) VALUES(10001,'H-001',1,'READY')");
        sql.update("INSERT INTO events(id,title,event_date_time,location,capacity,status) VALUES(10001,?,?,?,?,?)",
                "Hardening fixture event", LocalDateTime.now().plusDays(30), "Test hall", 20, "OPEN");
        sql.update("INSERT INTO tasks(id,title,department_id,assigned_employee_id,event_id,status,due_at,completed_at) VALUES"
                + "(10001,'Overdue fixture',10001,10002,10001,'ASSIGNED',?,NULL),"
                + "(10002,'Completed fixture',10002,NULL,10001,'COMPLETED',?,?),"
                + "(10003,'Open fixture',10001,NULL,10001,'OPEN',?,NULL)",
                LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(3));
        sql.update("INSERT INTO inventory_items(id,name,sku,quantity,reorder_threshold,department_id,active) VALUES"
                + "(10001,'Threshold fixture','H-001',2,2,10001,TRUE),"
                + "(10002,'Healthy fixture','H-002',8,2,10002,TRUE),"
                + "(10003,'Inactive fixture','H-003',0,2,10001,FALSE)");
        sql.update("INSERT INTO event_registrations(user_id,event_id) VALUES(10003,10001)");
        sql.update("INSERT INTO alerts(id,type,message,employee_id) VALUES"
                + "(10001,'SYSTEM','Test-only own alert',10002),(10002,'SYSTEM','Test-only other alert',10004)");
        Map<String, Object> fixture = new LinkedHashMap<>();
        fixture.put("baseUrl", "http://127.0.0.1:18081");
        fixture.put("testPassword", temporary);
        fixture.put("adminEmail", "hardening10001@example.test");
        fixture.put("staffEmail", "hardening10002@example.test");
        fixture.put("userEmail", "hardening10003@example.test");
        fixture.put("otherStaffEmail", "hardening10004@example.test");
        fixture.put("ineligibleStaffEmail", "hardening10006@example.test");
        fixture.put("departmentId", 10001); fixture.put("otherDepartmentId", 10002);
        fixture.put("employeeId", 10002); fixture.put("otherEmployeeId", 10004);
        fixture.put("alertId", 10001); fixture.put("otherAlertId", 10002);
        fixture.put("fixtureEventId", 10001);
        Path path = Path.of("target", "hardening", "fixtures.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, mapper.writeValueAsString(fixture));
    }

    @Test
    void physicalSchemaEnforcesNullableUniqueLinkAndNumericForeignKeyConstraints() {
        assertEquals(9, count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"));
        assertEquals("smallint", sql.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rooms' AND column_name='floor'", String.class));
        long department = department();
        employee(department); employee(department);
        assertEquals(2, count("SELECT COUNT(*) FROM employees WHERE department_id=" + department + " AND user_id IS NULL"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> sql.update(
                "INSERT INTO employees(user_id,name,email,department_id,job_role) VALUES(10002,'Duplicate link','duplicate-link@example.test',10001,'Test')"));
        var check = assertThrows(org.springframework.jdbc.UncategorizedSQLException.class, () -> sql.update(
                "INSERT INTO inventory_items(name,sku,quantity,reorder_threshold,department_id) VALUES('Invalid count','INVALID-COUNT',-1,0,10001)"));
        assertEquals(3819, check.getSQLException().getErrorCode(), "MySQL must enforce the quantity CHECK constraint.");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> sql.update(
                "INSERT INTO inventory_items(name,sku,quantity,reorder_threshold,department_id) VALUES('Invalid reference','INVALID-REF',0,0,9223372036854)"));
    }

    @Test
    void allPersistedAnalyticsReconcileWithIndependentSql() {
        JsonNode admin = mapper.valueToTree(analytics.dashboard(10001L, Role.ADMIN)).path("admin");
        Map<String, String> metrics = Map.ofEntries(
                Map.entry("activeRoomCount", "SELECT COUNT(*) FROM rooms WHERE active=TRUE"),
                Map.entry("readyRoomCount", "SELECT COUNT(*) FROM rooms WHERE active=TRUE AND status='READY'"),
                Map.entry("nonTerminalTaskCount", "SELECT COUNT(*) FROM tasks WHERE status IN ('OPEN','ASSIGNED','IN_PROGRESS')"),
                Map.entry("completedTaskCount", "SELECT COUNT(*) FROM tasks WHERE status='COMPLETED'"),
                Map.entry("activeDepartmentCount", "SELECT COUNT(*) FROM departments WHERE active=TRUE"),
                Map.entry("unresolvedAlertCount", "SELECT COUNT(*) FROM alerts WHERE status IN ('UNREAD','READ')"),
                Map.entry("activeInventoryItemCount", "SELECT COUNT(*) FROM inventory_items WHERE active=TRUE"),
                Map.entry("lowStockItemCount", "SELECT COUNT(*) FROM inventory_items WHERE active=TRUE AND quantity<=reorder_threshold"),
                Map.entry("nonTerminalEventCount", "SELECT COUNT(*) FROM events WHERE status IN ('DRAFT','OPEN','CLOSED','IN_PROGRESS')"),
                Map.entry("registrationCount", "SELECT COUNT(*) FROM event_registrations"));
        metrics.forEach((field, query) -> assertEquals(count(query), admin.path(field).asLong(), field));
        assertEquals(count("SELECT COUNT(*) FROM tasks WHERE due_at < NOW() AND status IN ('OPEN','ASSIGNED','IN_PROGRESS')"), admin.path("overdueTaskCount").asLong());
        JsonNode room = mapper.valueToTree(analytics.rooms(null, Role.ADMIN));
        assertEquals(count("SELECT COUNT(*) FROM rooms WHERE active=TRUE"), room.path("activeRoomCount").asLong());
        JsonNode task = mapper.valueToTree(analytics.tasks(null, Role.ADMIN));
        assertEquals(count("SELECT COUNT(*) FROM tasks"), task.path("totalTaskCount").asLong());
        JsonNode ops = mapper.valueToTree(analytics.operations(10001L, Role.ADMIN));
        assertEquals(count("SELECT COUNT(*) FROM inventory_items WHERE active=TRUE AND department_id=10001"), ops.path("inventory").path("activeInventoryItemCount").asLong());
        assertEquals(count("SELECT COUNT(*) FROM events"), ops.path("events").path("eventCount").asLong());
        assertEquals(count("SELECT COUNT(*) FROM event_registrations"), ops.path("events").path("registrationCount").asLong());
        assertEquals(count("SELECT COUNT(*) FROM tasks WHERE event_id IS NOT NULL"), ops.path("events").path("eventTaskCount").asLong());
        assertEquals(count("SELECT COUNT(*) FROM tasks WHERE event_id IS NOT NULL AND status='COMPLETED'"), ops.path("events").path("completedEventTaskCount").asLong());
        var summaries = analytics.departments(null, 0, 100, Role.ADMIN).getDepartments().getContent();
        summaries.forEach(row -> {
            assertEquals(count("SELECT COUNT(*) FROM employees WHERE status='ACTIVE' AND department_id=" + row.getDepartmentId()), row.getActiveEmployeeCount());
            assertEquals(count("SELECT COUNT(*) FROM tasks WHERE status IN ('OPEN','ASSIGNED','IN_PROGRESS') AND department_id=" + row.getDepartmentId()), row.getNonTerminalTaskCount());
        });
        JsonNode staff = mapper.valueToTree(analytics.dashboard(10002L, Role.STAFF)).path("staff");
        assertEquals(count("SELECT COUNT(*) FROM tasks WHERE assigned_employee_id=10002 AND status IN ('OPEN','ASSIGNED','IN_PROGRESS')"), staff.path("nonTerminalAssignedTaskCount").asLong());
        JsonNode user = mapper.valueToTree(analytics.dashboard(10003L, Role.USER));
        assertFalse(user.has("staff")); assertFalse(user.has("admin"));
        assertEquals(count("SELECT COUNT(*) FROM event_registrations WHERE user_id=10003"), user.path("user").path("registrationCount").asLong());
    }

    @Test
    void linkedDepartmentSynchronizationRollsBackAtomically() throws Exception {
        long department = department();
        long employee = IDS.incrementAndGet(), user = IDS.incrementAndGet();
        sql.update("INSERT INTO users(id,name,email,password_hash,role,department_id) VALUES(?,?,?,?,?,?)", user, "Rollback test", "rollback" + user + "@example.test", "test-only-unusable-hash", "STAFF", 10001);
        sql.update("INSERT INTO employees(id,user_id,name,email,department_id,job_role) VALUES(?,?,?,?,?,?)", employee, user, "Rollback test", "employee" + employee + "@example.test", 10001, "Test role");
        UpdateEmployeeRequest request = new UpdateEmployeeRequest();
        request.setName("Rollback test"); request.setEmail("employee" + employee + "@example.test");
        request.setDepartmentId(department); request.setJobRole("Test role"); request.setStatus(EmployeeStatus.ACTIVE);
        TransactionTemplate rollbackTransaction = new TransactionTemplate(transactions);
        Consumer<TransactionStatus> rollbackAction = status -> {
            employees.updateEmployee(employee, request);
            throw new IllegalStateException("Controlled test-only failure after linked update.");
        };
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> rollbackTransaction.executeWithoutResult(rollbackAction));
        assertEquals("Controlled test-only failure after linked update.", failure.getMessage());
        assertEquals(10001L, count("SELECT department_id FROM employees WHERE id=" + employee));
        assertEquals(10001L, count("SELECT department_id FROM users WHERE id=" + user));
        employees.updateEmployee(employee, request);
        assertEquals(department, count("SELECT department_id FROM employees WHERE id=" + employee));
        assertEquals(department, count("SELECT department_id FROM users WHERE id=" + user));
        UpdateEmployeeRequest second = mapper.convertValue(request, UpdateEmployeeRequest.class);
        second.setDepartmentId(department());
        race(() -> employees.updateEmployee(employee, request), () -> employees.updateEmployee(employee, second))
                .forEach(success -> assertTrue(success));
        assertEquals(count("SELECT department_id FROM employees WHERE id=" + employee),
                count("SELECT department_id FROM users WHERE id=" + user));
    }

    @Test
    void persistedRefreshRotationLogoutAndEmployeeDeactivationRevokeSessions() {
        AuthSession first = auth.login("hardening10003@example.test", fixturePassword, "test-only-hardening");
        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id=10003 AND refresh_token_hash IS NOT NULL AND refresh_token_expires_at IS NOT NULL"));
        AuthSession rotated = auth.refresh(first.getRawRefreshToken(), "test-only-hardening");
        String firstRefreshToken = first.getRawRefreshToken();
        String rotatedRefreshToken = rotated.getRawRefreshToken();
        RedactedToken firstEvidence = new RedactedToken(firstRefreshToken);
        RedactedToken rotatedEvidence = new RedactedToken(rotatedRefreshToken);
        assertEquals("[redacted]", firstEvidence.toString());
        assertEquals("[redacted]", rotatedEvidence.toString());
        assertNotEquals(firstEvidence, rotatedEvidence);
        assertThrows(InvalidRefreshTokenException.class, () -> auth.refresh(firstRefreshToken, "test-only-hardening"));
        auth.logout(10003L);
        auth.logout(10003L);
        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id=10003 AND refresh_token_hash IS NULL AND refresh_token_expires_at IS NULL"));
        assertThrows(InvalidRefreshTokenException.class, () -> auth.refresh(rotatedRefreshToken, "test-only-hardening"));
        long user = IDS.incrementAndGet(), employee = IDS.incrementAndGet();
        String email = "deactivation" + user + "@example.test";
        sql.update("INSERT INTO users(id,name,email,password_hash,role,department_id) VALUES(?,?,?,?,?,?)", user, "Deactivation test", email, passwords.encode(fixturePassword), "STAFF", 10002);
        sql.update("INSERT INTO employees(id,user_id,name,email,department_id,job_role) VALUES(?,?,?,?,?,?)", employee, user, "Deactivation test", email, 10002, "Test role");
        AuthSession session = auth.login(email, fixturePassword, "test-only-hardening");
        employees.deactivateEmployee(employee);
        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id=" + user + " AND status='INACTIVE' AND refresh_token_hash IS NULL AND refresh_token_expires_at IS NULL"));
        String revokedRefreshToken = session.getRawRefreshToken();
        assertThrows(InvalidRefreshTokenException.class, () -> auth.refresh(revokedRefreshToken, "test-only-hardening"));
        assertThrows(InvalidCredentialsException.class, () -> auth.login(email, fixturePassword, "test-only-hardening"));
    }

    /** Equality compares the real test token; failure diagnostics cannot print it. */
    private record RedactedToken(String value) {
        @Override public String toString() { return "[redacted]"; }
    }

    @Test
    void capacityAndDuplicateRegistrationSerialize() throws Exception {
        long event = event(1);
        List<Boolean> results = race(() -> registrations.register(event, 10003L), () -> registrations.register(event, 10005L));
        assertEquals(1, Collections.frequency(results, true));
        assertEquals(1, count("SELECT COUNT(*) FROM event_registrations WHERE event_id=" + event));
        long duplicate = event(5);
        results = race(() -> registrations.register(duplicate, 10003L), () -> registrations.register(duplicate, 10003L));
        assertEquals(1, Collections.frequency(results, true));
        assertEquals(1, count("SELECT COUNT(*) FROM event_registrations WHERE event_id=" + duplicate));
        assertTrue(registrations.listOwnRegistrations(10003L, 0, 20, "eventDateTime,asc").getTotalElements() > 0);
    }

    @Test
    void departmentDeactivationPreventsConcurrentTaskCreation() throws Exception {
        long department = department();
        blockedReference(() -> departments.deactivateDepartment(department), () -> tasks.createTask(task(department, null, null)));
    }

    @Test
    void employeeDeactivationPreventsConcurrentTaskAssignment() throws Exception {
        long department = department(), employee = employee(department);
        blockedReference(() -> employees.deactivateEmployee(employee), () -> tasks.createTask(task(department, employee, null)));
    }

    @Test
    void committedTaskAssignmentPreventsConcurrentEmployeeDeactivation() throws Exception {
        long department = department(), employee = employee(department);
        blockedReference(() -> tasks.createTask(task(department, employee, null)), () -> {
            employees.deactivateEmployee(employee);
            return null;
        });
    }

    @Test
    void roomDeactivationPreventsConcurrentTaskReference() throws Exception {
        long department = department(), room = IDS.incrementAndGet();
        sql.update("INSERT INTO rooms(id,room_number,floor) VALUES(?,?,1)", room, "H" + room);
        blockedReference(() -> rooms.deactivateRoom(room), () -> tasks.createTask(task(department, null, room)));
    }

    @Test
    void departmentDeactivationPreventsConcurrentInventoryCreation() throws Exception {
        long department = department();
        CreateInventoryItemRequest item = new CreateInventoryItemRequest();
        item.setName("Concurrent fixture"); item.setSku("H-" + IDS.incrementAndGet()); item.setDepartmentId(department);
        blockedReference(() -> departments.deactivateDepartment(department), () -> inventory.createInventoryItem(item));
    }

    @Test
    void concurrentAutomationIsIdempotentAndAutoResolves() throws Exception {
        race(() -> { automation.runChecks(); return true; }, () -> { automation.runChecks(); return true; })
                .forEach(success -> assertTrue(success));
        assertEquals(0, count("SELECT COUNT(*) FROM (SELECT employee_id,type,source_key FROM alerts WHERE status IN ('UNREAD','READ') AND source_key IS NOT NULL GROUP BY employee_id,type,source_key HAVING COUNT(*)>1) duplicates"));
        long before = count("SELECT COUNT(*) FROM alerts");
        automation.runChecks();
        assertEquals(before, count("SELECT COUNT(*) FROM alerts"));
        sql.update("UPDATE inventory_items SET quantity=10 WHERE id=10001");
        automation.checkInventory();
        assertEquals(0, count("SELECT COUNT(*) FROM alerts WHERE type='INVENTORY' AND status IN ('UNREAD','READ')"));
        sql.update("UPDATE inventory_items SET quantity=2 WHERE id=10001");
        automation.checkInventory();
        assertTrue(count("SELECT COUNT(*) FROM alerts") > before);
    }

    private long count(String query) { return Objects.requireNonNull(sql.queryForObject(query, Long.class)); }
    private long department() {
        long id = IDS.incrementAndGet();
        sql.update("INSERT INTO departments(id,name) VALUES(?,?)", id, "Hardening " + id);
        return id;
    }
    private long employee(long department) {
        long id = IDS.incrementAndGet();
        sql.update("INSERT INTO employees(id,name,email,department_id,job_role) VALUES(?,?,?,?,?)", id, "Hardening employee", "test" + id + "@example.test", department, "Test-only");
        return id;
    }
    private long event(int capacity) {
        long id = IDS.incrementAndGet();
        sql.update("INSERT INTO events(id,title,event_date_time,location,capacity,status) VALUES(?,?,?,?,?,'OPEN')", id, "Concurrent test", LocalDateTime.now().plusDays(20), "Test hall", capacity);
        return id;
    }
    private CreateTaskRequest task(long department, Long employee, Long room) {
        CreateTaskRequest task = new CreateTaskRequest();
        task.setTitle("Concurrent test-only task"); task.setDepartmentId(department);
        task.setAssignedEmployeeId(employee); task.setRoomId(room);
        return task;
    }
    private List<Boolean> race(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<?> action : List.of(first, second)) futures.add(pool.submit(() -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                try { action.call(); return true; } catch (ConflictException expected) { return false; }
            }));
            start.countDown();
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    private void blockedReference(Runnable deactivate, Callable<?> create) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                deactivate.run(); held.countDown();
                try { assertTrue(release.await(20, TimeUnit.SECONDS)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            }));
            assertTrue(held.await(10, TimeUnit.SECONDS));
            Future<Boolean> second = pool.submit(() -> {
                try { create.call(); return false; } catch (ConflictException expected) { return true; }
            });
            // Observe a real InnoDB wait, not a timing assumption or test-only mocked repository.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean waiting = false;
            while (!waiting && System.nanoTime() < deadline && !second.isDone()) {
                waiting = count("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON w.REQUESTING_ENGINE_LOCK_ID=l.ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA='rockey_hospitality_hardening'") > 0;
                Thread.onSpinWait();
            }
            assertTrue(waiting, "Contender must reach the actual disposable-schema lock wait.");
            release.countDown(); first.get(20, TimeUnit.SECONDS);
            assertTrue(second.get(20, TimeUnit.SECONDS), "The losing conflicting mutation must be rejected after the preceding commit.");
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}

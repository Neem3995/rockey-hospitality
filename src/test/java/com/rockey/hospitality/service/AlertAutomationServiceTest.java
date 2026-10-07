package com.rockey.hospitality.service;

import com.rockey.hospitality.entity.*;
import com.rockey.hospitality.entity.Alert;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.Room;
import com.rockey.hospitality.entity.Task;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.repository.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlertAutomationServiceTest {
    private AlertRepository alerts;
    private EmployeeRepository employees;
    private RoomRepository roomRepository;
    private TaskRepository taskRepository;
    private InventoryItemRepository inventoryRepository;
    private AlertAutomationService service;
    private final LocalDateTime now = LocalDateTime.of(2030, 1, 1, 12, 0);
    private List<Alert> rows;
    private List<Room> rooms;
    private List<Task> tasks;
    private List<InventoryItem> items;
    private Map<Long, Employee> recipients;
    private List<Long> housekeeping;
    private List<Long> purchasing;
    private List<Long> admins;
    private AtomicReference<Instant> time;

    @BeforeEach
    void setUp() {
        alerts = mock(AlertRepository.class);
        employees = mock(EmployeeRepository.class);
        roomRepository = mock(RoomRepository.class);
        taskRepository = mock(TaskRepository.class);
        inventoryRepository = mock(InventoryItemRepository.class);
        rows = new ArrayList<>(); rooms = new ArrayList<>(); tasks = new ArrayList<>(); items = new ArrayList<>();
        recipients = Map.of(1L, employee(1L, "Housekeeping"), 2L, employee(2L, "Purchasing"),
                3L, employee(3L, "Administration"), 4L, employee(4L, "Housekeeping"));
        housekeeping = List.of(1L); purchasing = List.of(2L); admins = List.of(3L);
        time = new AtomicReference<>(now.atZone(ZoneId.systemDefault()).toInstant());
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(i -> time.get());
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        when(clock.withZone(any())).thenReturn(clock);
        service = new AlertAutomationService(alerts, employees, roomRepository, taskRepository, inventoryRepository, clock);
        when(roomRepository.findReadinessAlertSources(any(), any(), any())).thenAnswer(i -> rooms);
        when(taskRepository.findTaskAlertSources(any(), anyCollection(), anyCollection())).thenAnswer(i -> tasks);
        when(inventoryRepository.findInventoryAlertSources()).thenAnswer(i -> items);
        when(employees.findActiveRecipientIdsByDepartment(eq("Housekeeping"), eq(Employee.Status.ACTIVE))).thenAnswer(i -> housekeeping);
        when(employees.findActiveRecipientIdsByDepartment(eq("Purchasing"), eq(Employee.Status.ACTIVE))).thenAnswer(i -> purchasing);
        when(employees.findActiveAdminRecipientIds(Employee.Status.ACTIVE, User.Role.ADMIN, User.Status.ACTIVE)).thenAnswer(i -> admins);
        when(employees.findByIdForUpdate(anyLong())).thenAnswer(i -> Optional.ofNullable(recipients.get(i.getArgument(0))));
        when(alerts.findUnresolvedRecipientIds(any(), anyCollection())).thenAnswer(i -> rows.stream()
                .filter(a -> a.getType() == i.getArgument(0) && a.getStatus() != Alert.Status.RESOLVED && a.getSourceKey() != null)
                .map(a -> a.getEmployee().getId()).distinct().toList());
        when(alerts.findUnresolvedForUpdate(anyLong(), any(), anyCollection())).thenAnswer(i -> rows.stream()
                .filter(a -> a.getEmployee().getId().equals(i.getArgument(0)) && a.getType() == i.getArgument(1)
                        && a.getStatus() != Alert.Status.RESOLVED && a.getSourceKey() != null).toList());
        when(alerts.save(any(Alert.class))).thenAnswer(i -> {
            Alert a = i.getArgument(0);
            if (a.getId() == null) { ReflectionTestUtils.setField(a, "id", (long) rows.size() + 1); rows.add(a); }
            return a;
        });
    }

    @ParameterizedTest
    @CsvSource({"-1,false", "0,true", "120,true", "121,false"})
    void roomWindowIncludesNowAndExactlyTwoHoursButNotPastOrBeyond(int minutes, boolean expected) {
        rooms.add(room(now.plusMinutes(minutes)));
        service.checkRoomReadiness();
        assertThat(rows).hasSize(expected ? 1 : 0);
        verify(roomRepository).findReadinessAlertSources(now, now.plusHours(2), Room.Status.READY);
        if (expected) {
            assertThat(rows.get(0).getType()).isEqualTo(Alert.Type.ROOM);
            assertThat(rows.get(0).getTask()).isNull();
            assertThat(rows.get(0).getCreatedAt()).isEqualTo(now);
            assertThat(rows.get(0).getSeverity()).isEqualTo(Alert.Severity.INFO);
        }
    }

    @Test
    void roomAlertsReachEveryActiveHousekeepingEmployee() {
        housekeeping = List.of(1L, 4L);
        rooms.add(room(now.plusHours(1)));
        service.checkRoomReadiness();
        assertThat(rows).extracting(a -> a.getEmployee().getId()).containsExactly(1L, 4L);
    }

    @ParameterizedTest
    @EnumSource(value = Room.Status.class, names = {"READY"})
    void readyRoomCreatesNoAlert(Room.Status status) {
        Room room = room(now.plusHours(1)); room.updateStatus(status); rooms.add(room);
        service.checkRoomReadiness(); assertThat(rows).isEmpty();
    }

    @Test
    void inactiveOrUnscheduledRoomCreatesNoAlert() {
        Room room = room(now.plusHours(1)); room.deactivate(); rooms.add(room); rooms.add(room(null));
        service.checkRoomReadiness(); assertThat(rows).isEmpty();
    }

    @Test
    void roomConditionClearsAndCanRecurWithNewHistory() {
        Room room = room(now.plusHours(1)); rooms.add(room);
        service.checkRoomReadiness();
        Alert original = rows.get(0);
        room.updateStatus(Room.Status.READY);
        time.set(time.get().plusSeconds(60));
        service.checkRoomReadiness();
        assertThat(original.getStatus()).isEqualTo(Alert.Status.RESOLVED);
        assertThat(original.getReadAt()).isEqualTo(now.plusMinutes(1));
        assertThat(original.getResolvedAt()).isEqualTo(now.plusMinutes(1));
        room.updateStatus(Room.Status.DIRTY);
        service.checkRoomReadiness();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).getStatus()).isEqualTo(Alert.Status.UNREAD);
        assertThat(rows.get(1).getSourceKey()).isEqualTo(original.getSourceKey());
    }

    @Test
    void roomPassingArrivalWindowAutoResolvesExistingAlert() {
        rooms.add(room(now)); service.checkRoomReadiness();
        time.set(time.get().plusSeconds(1)); service.checkRoomReadiness();
        assertThat(rows.get(0).getStatus()).isEqualTo(Alert.Status.RESOLVED);
    }

    @Test
    void roomJustBeyondTwoHoursByOneNanosecondDoesNotGenerate() {
        rooms.add(room(now.plusHours(2).plusNanos(1)));
        service.checkRoomReadiness();
        assertThat(rows).isEmpty();
    }

    @Test
    void clearingReadAlertPreservesFirstReadTimestampAndRecordsResolution() {
        Room room = room(now.plusHours(1)); rooms.add(room); service.checkRoomReadiness();
        rows.get(0).markRead(now);
        time.set(time.get().plusSeconds(60)); room.updateStatus(Room.Status.READY); service.checkRoomReadiness();
        assertThat(rows.get(0).getReadAt()).isEqualTo(now);
        assertThat(rows.get(0).getResolvedAt()).isEqualTo(now.plusMinutes(1));
    }

    @Test
    void unchangedReadAlertSuppressesDuplicatesAndPreservesReadTimestamp() {
        rooms.add(room(now.plusHours(1))); service.checkRoomReadiness();
        rows.get(0).markRead(now);
        time.set(time.get().plusSeconds(60)); service.checkRoomReadiness(); service.checkRoomReadiness();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(Alert.Status.READ);
        assertThat(rows.get(0).getReadAt()).isEqualTo(now);
    }

    @Test
    void manualResolutionAllowsNewAlertWhileConditionIsTrue() {
        rooms.add(room(now.plusHours(1))); service.checkRoomReadiness();
        rows.get(0).resolve(now); service.checkRoomReadiness();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getStatus()).isEqualTo(Alert.Status.RESOLVED);
        assertThat(rows.get(1).getStatus()).isEqualTo(Alert.Status.UNREAD);
    }

    @Test
    void noHousekeepingRecipientDoesNotInventFallback() {
        housekeeping = List.of(); rooms.add(room(now.plusHours(1)));
        service.checkRoomReadiness(); assertThat(rows).isEmpty();
    }

    @Test
    void inactiveRecipientGuardPreventsGenerationAndResolvesExistingAlerts() {
        rooms.add(room(now.plusHours(1))); service.checkRoomReadiness();
        recipients.get(1L).deactivate(); service.checkRoomReadiness();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(Alert.Status.RESOLVED);
    }

    @Test
    void inactiveDepartmentCannotReceiveNewAlerts() {
        recipients.get(1L).getDepartment().deactivate(); rooms.add(room(now.plusHours(1)));
        service.checkRoomReadiness(); assertThat(rows).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"-1,true", "0,false", "1,false"})
    void overdueIsStrictlyBeforeServerTime(int seconds, boolean expected) {
        tasks.add(task(Task.Priority.LOW, now.plusSeconds(seconds)));
        service.checkTasks(); assertThat(rows).hasSize(expected ? 1 : 0);
        if (expected) assertThat(rows.get(0).getTask()).isSameAs(tasks.get(0));
    }

    @ParameterizedTest
    @EnumSource(Task.Priority.class)
    void onlyHighAndUrgentCreatePriorityAlertsWithoutDueDate(Task.Priority priority) {
        tasks.add(task(priority, null)); service.checkTasks();
        assertThat(rows).hasSize(priority == Task.Priority.HIGH || priority == Task.Priority.URGENT ? 1 : 0);
    }

    @Test
    void overdueAndHighPriorityHaveSeparateStableDedupKeys() {
        Task task = task(Task.Priority.HIGH, now.minusMinutes(1)); tasks.add(task);
        service.checkTasks(); service.checkTasks();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(Alert::getSourceKey).containsExactlyInAnyOrder("TASK:10:OVERDUE", "TASK:10:HIGH_PRIORITY");
        ReflectionTestUtils.setField(task, "dueAt", now.plusHours(1)); service.checkTasks();
        assertThat(rows.stream().filter(a -> a.getSourceKey().endsWith("OVERDUE")).findFirst().orElseThrow().getStatus())
                .isEqualTo(Alert.Status.RESOLVED);
        assertThat(rows.stream().filter(a -> a.getSourceKey().endsWith("HIGH_PRIORITY")).findFirst().orElseThrow().getStatus())
                .isEqualTo(Alert.Status.UNREAD);
    }

    @ParameterizedTest
    @EnumSource(value = Task.Status.class, names = {"COMPLETED", "CANCELLED"})
    void terminalTaskAutoResolvesBothConditionsAndCreatesNothing(Task.Status status) {
        Task task = task(Task.Priority.URGENT, now.minusMinutes(1)); tasks.add(task);
        service.checkTasks(); ReflectionTestUtils.setField(task, "status", status); service.checkTasks();
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(a -> a.getStatus() == Alert.Status.RESOLVED);
    }

    @Test
    void unassignmentResolvesTaskAlerts() {
        Task task = task(Task.Priority.HIGH, null); tasks.add(task); service.checkTasks();
        task.assign(null); service.checkTasks();
        assertThat(rows.get(0).getStatus()).isEqualTo(Alert.Status.RESOLVED);
    }

    @Test
    void reassignmentResolvesFormerRecipientAndNotifiesNewAssignee() {
        Task task = task(Task.Priority.HIGH, null); tasks.add(task); service.checkTasks();
        task.assign(recipients.get(4L)); service.checkTasks();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getStatus()).isEqualTo(Alert.Status.RESOLVED);
        assertThat(rows.get(1).getEmployee().getId()).isEqualTo(4L);
    }

    @Test
    void inactiveTaskAssigneeDoesNotReceiveAlerts() {
        recipients.get(1L).deactivate(); tasks.add(task(Task.Priority.URGENT, now.minusMinutes(1)));
        service.checkTasks(); assertThat(rows).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0,0,true", "10,10,true", "9,10,true", "11,10,false"})
    void inventoryUsesInclusiveNonNegativeThreshold(int quantity, int threshold, boolean expected) {
        items.add(item(quantity, threshold)); service.checkInventory();
        assertThat(rows).hasSize(expected ? 1 : 0);
        if (expected) { assertThat(rows.get(0).getEmployee().getId()).isEqualTo(2L); assertThat(rows.get(0).getTask()).isNull(); }
        verify(employees, never()).findActiveAdminRecipientIds(any(), any(), any());
    }

    @Test
    void purchasingAbsenceUsesActiveAdminFallback() {
        purchasing = List.of(); items.add(item(0, 10)); service.checkInventory();
        assertThat(rows.get(0).getEmployee().getId()).isEqualTo(3L);
        verify(employees).findActiveAdminRecipientIds(Employee.Status.ACTIVE, User.Role.ADMIN, User.Status.ACTIVE);
    }

    @Test
    void noPurchasingOrAdminDoesNotInventRecipient() {
        purchasing = List.of(); admins = List.of(); items.add(item(0, 10));
        service.checkInventory(); assertThat(rows).isEmpty();
    }

    @Test
    void inactiveFallbackEmployeeIsRejected() {
        purchasing = List.of(); recipients.get(3L).deactivate(); items.add(item(0, 10));
        service.checkInventory(); assertThat(rows).isEmpty();
    }

    @Test
    void restockingResolvesInventoryAndConditionRecurrenceCreatesNewRow() {
        InventoryItem item = item(0, 10); items.add(item); service.checkInventory(); service.checkInventory();
        assertThat(rows).hasSize(1);
        item.updateDetails("Towels", 11, 10, item.getDepartment(), true); service.checkInventory();
        assertThat(rows.get(0).getStatus()).isEqualTo(Alert.Status.RESOLVED);
        item.updateDetails("Towels", 10, 10, item.getDepartment(), true); service.checkInventory();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).getStatus()).isEqualTo(Alert.Status.UNREAD);
    }

    @Test
    void inactiveInventoryResolvesWithoutDeletingHistory() {
        InventoryItem item = item(0, 10); items.add(item); service.checkInventory();
        item.deactivate(); service.checkInventory();
        assertThat(rows.get(0).getStatus()).isEqualTo(Alert.Status.RESOLVED);
        verify(alerts, never()).delete(any());
    }

    @Test
    void fallbackRecipientChangesResolveFormerRecipient() {
        purchasing = List.of(); items.add(item(0, 10)); service.checkInventory();
        purchasing = List.of(2L); service.checkInventory();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getStatus()).isEqualTo(Alert.Status.RESOLVED);
        assertThat(rows.get(1).getEmployee().getId()).isEqualTo(2L);
    }

    @Test
    void deletedOrMissingSourcesResolveExistingDerivedAlerts() {
        rooms.add(room(now.plusHours(1))); service.checkRoomReadiness(); rooms.clear(); service.checkRoomReadiness();
        assertThat(rows.get(0).getStatus()).isEqualTo(Alert.Status.RESOLVED);
    }

    @Test
    void systemAndNonDerivedAlertsAreNotResolvedByScans() {
        Alert system = new Alert(Alert.Type.SYSTEM, "Manual system notice", recipients.get(1L), null, null, now);
        ReflectionTestUtils.setField(system, "id", 100L); rows.add(system);
        service.runChecks(); assertThat(system.getStatus()).isEqualTo(Alert.Status.UNREAD);
    }

    @Test
    void combinedScanIsIdempotentAcrossAllThreeTypes() {
        rooms.add(room(now.plusHours(1))); tasks.add(task(Task.Priority.HIGH, null)); items.add(item(0, 10));
        service.runChecks(); service.runChecks();
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(Alert::getType).containsExactly(Alert.Type.ROOM, Alert.Type.TASK, Alert.Type.INVENTORY);
        verify(employees, atLeastOnce()).findByIdForUpdate(1L);
    }

    @Test
    void injectedUtcInstantUsesExistingServerLocalRoomAndTaskTimeSemantics() {
        Instant instant = Instant.parse("2030-01-01T12:00:00Z");
        LocalDateTime serverNow = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        AlertAutomationService localService = new AlertAutomationService(alerts, employees,
                roomRepository, taskRepository, inventoryRepository, Clock.fixed(instant, ZoneOffset.UTC));
        rooms.add(room(serverNow.plusHours(1)));
        tasks.add(task(Task.Priority.LOW, serverNow.plusHours(1)));
        localService.runChecks();
        verify(roomRepository).findReadinessAlertSources(serverNow, serverNow.plusHours(2), Room.Status.READY);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getType()).isEqualTo(Alert.Type.ROOM);
        assertThat(rows.get(0).getCreatedAt()).isEqualTo(serverNow);
    }

    @Test
    void transactionInterceptorRollsBackWholeScanOnFailureWithoutSwallowingError() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        SimpleTransactionStatus transaction = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(transaction);
        ProxyFactory factory = new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        AlertAutomationService transactional = (AlertAutomationService) factory.getProxy();
        rooms.add(room(now.plusHours(1)));
        when(taskRepository.findTaskAlertSources(any(), anyCollection(), anyCollection())).thenThrow(new IllegalStateException("Simulated persistence failure"));
        assertThatThrownBy(transactional::runChecks).isInstanceOf(IllegalStateException.class);
        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
        verify(inventoryRepository, never()).findInventoryAlertSources();
    }

    private Employee employee(Long id, String departmentName) {
        Department department = new Department(departmentName, null);
        Employee employee = new Employee("Employee " + id, "employee" + id + "@example.test", department, "Worker", null);
        ReflectionTestUtils.setField(employee, "id", id); return employee;
    }

    private Room room(LocalDateTime arrival) {
        Room room = new Room("218", "STANDARD", 2, Room.Status.DIRTY, arrival);
        ReflectionTestUtils.setField(room, "id", 1L); return room;
    }

    private Task task(Task.Priority priority, LocalDateTime due) {
        Task task = new Task("Inspect room", null, recipients.get(1L).getDepartment(), recipients.get(1L), null, priority, due);
        ReflectionTestUtils.setField(task, "id", 10L); return task;
    }

    private InventoryItem item(int quantity, int threshold) {
        InventoryItem item = new InventoryItem("Towels", "HK-TOWELS", quantity, threshold, recipients.get(1L).getDepartment());
        ReflectionTestUtils.setField(item, "id", 20L); return item;
    }
}

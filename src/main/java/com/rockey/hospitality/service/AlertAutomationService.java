package com.rockey.hospitality.service;

import com.rockey.hospitality.entity.Alert;
import com.rockey.hospitality.entity.AlertStatus;
import com.rockey.hospitality.entity.AlertType;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.EmployeeStatus;
import com.rockey.hospitality.entity.InventoryItem;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.Room;
import com.rockey.hospitality.entity.RoomStatus;
import com.rockey.hospitality.entity.Task;
import com.rockey.hospitality.entity.TaskPriority;
import com.rockey.hospitality.entity.TaskStatus;
import com.rockey.hospitality.entity.UserStatus;
import com.rockey.hospitality.repository.AlertRepository;
import com.rockey.hospitality.repository.EmployeeRepository;
import com.rockey.hospitality.repository.InventoryItemRepository;
import com.rockey.hospitality.repository.RoomRepository;
import com.rockey.hospitality.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Reconciles Room, Task, and Inventory conditions with recipients' unresolved alerts.
 * Cleared conditions resolve old rows; recurring conditions can create new rows without erasing history.
 */
// Registers this business/security service for constructor injection.
@Service
public class AlertAutomationService {

    /**
     * UNREAD and READ states both remain unresolved for alert reconciliation.
     */
    public static final List<AlertStatus> UNRESOLVED = List.of(AlertStatus.UNREAD, AlertStatus.READ);
    /**
     * COMPLETED and CANCELLED Task states are terminal and excluded from active-work conditions.
     */
    private static final List<TaskStatus> TERMINAL = List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED);
    /**
     * HIGH and URGENT Task priorities produce priority-alert conditions.
     */
    private static final List<TaskPriority> HIGH_PRIORITIES = List.of(TaskPriority.HIGH, TaskPriority.URGENT);
    /**
     * Injected AlertRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final AlertRepository alertRepository;
    /**
     * Injected EmployeeRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final EmployeeRepository employeeRepository;
    /**
     * Injected RoomRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final RoomRepository roomRepository;
    /**
     * Injected TaskRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final TaskRepository taskRepository;
    /**
     * Injected InventoryItemRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final InventoryItemRepository inventoryRepository;
    /**
     * Injected Clock adapted to the server zone used by existing operational LocalDateTime values.
     */
    private final Clock clock;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public AlertAutomationService(AlertRepository alertRepository, EmployeeRepository employeeRepository,
                                  RoomRepository roomRepository, TaskRepository taskRepository,
                                  InventoryItemRepository inventoryRepository, Clock clock) {
        this.alertRepository = alertRepository;
        this.employeeRepository = employeeRepository;
        this.roomRepository = roomRepository;
        this.taskRepository = taskRepository;
        this.inventoryRepository = inventoryRepository;
        // Existing Room/Task APIs store server-local LocalDateTime values, not UTC instants.
        this.clock = clock.withZone(ZoneId.systemDefault());
    }

    /**
     * Uses one server-local time snapshot to scan Room, Task, and Inventory sources.
     * A runtime failure rolls back this transaction rather than retaining a partial scan.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes.
    @Transactional
    public void runChecks() {
        // One transaction reconciles source conditions; a failed scan must not leave half its alerts.
        LocalDateTime now = LocalDateTime.now(clock);
        scanRooms(now);
        scanTasks(now);
        scanInventory(now);
    }

    /**
     * Reconciles only Room readiness alerts using the current server-local time.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes.
    @Transactional
    public void checkRoomReadiness() { scanRooms(LocalDateTime.now(clock)); }

    /**
     * Reconciles only assigned Task overdue and high-priority alerts.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes.
    @Transactional
    public void checkTasks() { scanTasks(LocalDateTime.now(clock)); }

    /**
     * Reconciles only active Inventory threshold alerts.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes.
    @Transactional
    public void checkInventory() { scanInventory(LocalDateTime.now(clock)); }

    /**
     * Finds active, non-READY Rooms with arrivals between now and two hours ahead, including both boundaries.
     * Expected alerts are addressed to active Housekeeping employees.
     */
    private void scanRooms(LocalDateTime now) {
        Map<Long, List<Condition>> expected = new TreeMap<>();
        List<Long> recipients = employeeRepository.findActiveRecipientIdsByDepartment("Housekeeping", EmployeeStatus.ACTIVE);
        for (Room room : roomRepository.findReadinessAlertSources(now, now.plusHours(2), RoomStatus.READY)) {
            if (!Boolean.TRUE.equals(room.getActive()) || room.getStatus() == RoomStatus.READY
                    || room.getNextArrivalAt() == null || room.getNextArrivalAt().isBefore(now)
                    || room.getNextArrivalAt().isAfter(now.plusHours(2))) continue;
            Condition condition = new Condition("ROOM:" + room.getId() + ":ARRIVAL_NOT_READY",
                    "Room " + room.getRoomNumber() + " is not READY for arrival within two hours.", null);
            recipients.forEach(id -> add(expected, id, condition));
        }
        reconcile(AlertType.ROOM, expected, now);
    }

    /**
     * Builds separate overdue and HIGH/URGENT conditions for assigned, non-terminal Tasks.
     * Due time must be strictly before now to count as overdue.
     */
    private void scanTasks(LocalDateTime now) {
        Map<Long, List<Condition>> expected = new TreeMap<>();
        for (Task task : taskRepository.findTaskAlertSources(now, TERMINAL, HIGH_PRIORITIES)) {
            if (task.getAssignedEmployee() == null || TERMINAL.contains(task.getStatus())) continue;
            Long recipient = task.getAssignedEmployee().getId();
            if (task.getDueAt() != null && task.getDueAt().isBefore(now)) {
                add(expected, recipient, new Condition("TASK:" + task.getId() + ":OVERDUE",
                        "Task " + task.getId() + " is overdue.", task));
            }
            if (HIGH_PRIORITIES.contains(task.getPriority())) {
                add(expected, recipient, new Condition("TASK:" + task.getId() + ":HIGH_PRIORITY",
                        "Task " + task.getId() + " has HIGH/URGENT priority.", task));
            }
        }
        reconcile(AlertType.TASK, expected, now);
    }

    /**
     * Creates conditions for active stock at or below its threshold.
     * Active Purchasing recipients are preferred; eligible ADMIN employees are the fallback.
     */
    private void scanInventory(LocalDateTime now) {
        Map<Long, List<Condition>> expected = new TreeMap<>();
        List<Long> recipients = employeeRepository.findActiveRecipientIdsByDepartment("Purchasing", EmployeeStatus.ACTIVE);
        if (recipients.isEmpty()) {
            recipients = employeeRepository.findActiveAdminRecipientIds(EmployeeStatus.ACTIVE, Role.ADMIN, UserStatus.ACTIVE);
        }
        for (InventoryItem item : inventoryRepository.findInventoryAlertSources()) {
            if (!Boolean.TRUE.equals(item.getActive()) || item.getQuantity() > item.getReorderThreshold()) continue;
            Condition condition = new Condition("INVENTORY:" + item.getId() + ":AT_OR_BELOW_THRESHOLD",
                    "Inventory " + item.getSku() + " is at or below its reorder threshold.", null);
            for (Long id : recipients) add(expected, id, condition);
        }
        reconcile(AlertType.INVENTORY, expected, now);
    }

    /**
     * Groups a source condition under its intended employee recipient for reconciliation.
     */
    private void add(Map<Long, List<Condition>> expected, Long id, Condition condition) {
        expected.computeIfAbsent(id, ignored -> new ArrayList<>()).add(condition);
    }

    /**
     * Visits both currently expected recipients and recipients with older unresolved source alerts.
     * This allows disappeared conditions to resolve as well as new ones to generate.
     */
    private void reconcile(AlertType type, Map<Long, List<Condition>> expected, LocalDateTime now) {
        Set<Long> recipientIds = new TreeSet<>(expected.keySet());
        recipientIds.addAll(alertRepository.findUnresolvedRecipientIds(type, UNRESOLVED));
        for (Long id : recipientIds) {
            reconcileRecipient(id, type, expected.getOrDefault(id, List.of()), now);
        }
    }

    /**
     * Locks the Employee and unresolved alerts before retaining one row per desired source key.
     * Ineligible recipients, cleared conditions, and old duplicates resolve; missing desired conditions create new rows.
     */
    private void reconcileRecipient(Long id, AlertType type, List<Condition> expected, LocalDateTime now) {
        // Serialize generation for each recipient before reading its unresolved alerts, within the caller's transaction.
        Employee employee = employeeRepository.findByIdForUpdate(id).orElse(null);
        if (employee == null) return;
        boolean eligible = employee.getStatus() == EmployeeStatus.ACTIVE
                && Boolean.TRUE.equals(employee.getDepartment().getActive());
        List<Condition> conditions = eligible ? expected : List.of();
        Map<String, Condition> desired = new TreeMap<>();
        conditions.forEach(condition -> desired.put(condition.key(), condition));
        Set<String> retained = new HashSet<>();
        for (Alert alert : alertRepository.findUnresolvedForUpdate(id, type, UNRESOLVED)) {
            if (desired.containsKey(alert.getSourceKey()) && retained.add(alert.getSourceKey())) continue;
            // Cleared conditions, former recipients, and any legacy duplicates resolve without deletion.
            alert.resolve(now);
            alertRepository.save(alert);
        }
        for (Condition condition : desired.values()) {
            if (!retained.contains(condition.key())) {
                alertRepository.save(new Alert(type, condition.message(), employee, condition.task(), condition.key(), now));
            }
        }
    }

    /**
     * Describes one expected automated alert: a stable source key, safe message, and optional related Task.
     */
    private record Condition(
            /**
             * Stable source-condition identity used to reconcile unresolved alerts.
             */
            String key,
            /**
             * Safe client-facing explanatory text without private authentication or database details.
             */
            String message,
            /**
             * Optional Task summary or association providing alert/work context.
             */
            Task task) { }
}

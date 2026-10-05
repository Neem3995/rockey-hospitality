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

@Service
public class AlertAutomationService {

    public static final List<AlertStatus> UNRESOLVED = List.of(AlertStatus.UNREAD, AlertStatus.READ);
    private static final List<TaskStatus> TERMINAL = List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED);
    private static final List<TaskPriority> HIGH_PRIORITIES = List.of(TaskPriority.HIGH, TaskPriority.URGENT);
    private final AlertRepository alertRepository;
    private final EmployeeRepository employeeRepository;
    private final RoomRepository roomRepository;
    private final TaskRepository taskRepository;
    private final InventoryItemRepository inventoryRepository;
    private final Clock clock;

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

    @Transactional
    public void runChecks() {
        LocalDateTime now = LocalDateTime.now(clock);
        scanRooms(now);
        scanTasks(now);
        scanInventory(now);
    }

    @Transactional
    public void checkRoomReadiness() { scanRooms(LocalDateTime.now(clock)); }

    @Transactional
    public void checkTasks() { scanTasks(LocalDateTime.now(clock)); }

    @Transactional
    public void checkInventory() { scanInventory(LocalDateTime.now(clock)); }

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

    private void add(Map<Long, List<Condition>> expected, Long id, Condition condition) {
        expected.computeIfAbsent(id, ignored -> new ArrayList<>()).add(condition);
    }

    private void reconcile(AlertType type, Map<Long, List<Condition>> expected, LocalDateTime now) {
        Set<Long> recipientIds = new TreeSet<>(expected.keySet());
        recipientIds.addAll(alertRepository.findUnresolvedRecipientIds(type, UNRESOLVED));
        for (Long id : recipientIds) {
            reconcileRecipient(id, type, expected.getOrDefault(id, List.of()), now);
        }
    }

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

    private record Condition(String key, String message, Task task) { }
}

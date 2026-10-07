package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.EmployeeStatus;
import com.rockey.hospitality.entity.TaskStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** Independent aggregates avoid multiplying counts through unrelated collection joins. */
@Repository
public class AnalyticsRepository {
    private static final String STATUSES_PARAMETER = "statuses";
    private static final List<TaskStatus> NON_TERMINAL = List.of(
            TaskStatus.OPEN, TaskStatus.ASSIGNED, TaskStatus.IN_PROGRESS);
    private final EntityManager entityManager;

    public AnalyticsRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    public long countRegistrations(Long userId) {
        // Only fixed query fragments are appended; user values are bound, never interpolated.
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(event) FROM User user JOIN user.registeredEvents event"
                        + (userId == null ? "" : " WHERE user.id = :userId"), Long.class);
        if (userId != null) query.setParameter("userId", userId);
        return query.getSingleResult();
    }

    public List<Object[]> roomCounts(Integer floor) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT room.status, COUNT(room) FROM Room room WHERE room.active = TRUE"
                        + (floor == null ? "" : " AND room.floor = :floor")
                        + " GROUP BY room.status", Object[].class);
        if (floor != null) query.setParameter("floor", floor);
        return query.getResultList();
    }

    public List<Object[]> taskCounts(Long departmentId, Long employeeId) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT task.status, COUNT(task) FROM Task task WHERE 1 = 1"
                        + taskScope(departmentId, employeeId) + " GROUP BY task.status", Object[].class);
        bindTaskScope(query, departmentId, employeeId);
        return query.getResultList();
    }

    public long overdueTaskCount(Long departmentId, Long employeeId, LocalDateTime now) {
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(task) FROM Task task WHERE task.status IN :statuses"
                        + " AND task.dueAt IS NOT NULL AND task.dueAt < :now"
                        + taskScope(departmentId, employeeId), Long.class);
        query.setParameter(STATUSES_PARAMETER, NON_TERMINAL);
        query.setParameter("now", now);
        bindTaskScope(query, departmentId, employeeId);
        return query.getSingleResult();
    }

    public List<Object[]> alertCounts(Long employeeId) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT alert.status, COUNT(alert) FROM Alert alert"
                        + (employeeId == null ? "" : " WHERE alert.employee.id = :employeeId")
                        + " GROUP BY alert.status", Object[].class);
        if (employeeId != null) query.setParameter("employeeId", employeeId);
        return query.getResultList();
    }

    public long activeInventoryItemCount(Long departmentId) {
        return inventoryCount(departmentId, false);
    }

    public long lowStockItemCount(Long departmentId) { return inventoryCount(departmentId, true); }

    private long inventoryCount(Long departmentId, boolean lowStock) {
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(item) FROM InventoryItem item WHERE item.active = TRUE"
                        + (departmentId == null ? "" : " AND item.department.id = :departmentId")
                        + (lowStock ? " AND item.quantity <= item.reorderThreshold" : ""), Long.class);
        if (departmentId != null) query.setParameter("departmentId", departmentId);
        return query.getSingleResult();
    }

    public List<Object[]> eventCounts() {
        return entityManager.createQuery(
                "SELECT event.status, COUNT(event) FROM Event event GROUP BY event.status",
                Object[].class).getResultList();
    }

    public long eventTaskCount() {
        return entityManager.createQuery(
                "SELECT COUNT(task) FROM Task task WHERE task.event IS NOT NULL", Long.class)
                .getSingleResult();
    }

    public long completedEventTaskCount() {
        return entityManager.createQuery(
                "SELECT COUNT(task) FROM Task task WHERE task.event IS NOT NULL AND task.status = :status",
                Long.class).setParameter("status", TaskStatus.COMPLETED).getSingleResult();
    }

    public long activeDepartmentCount() {
        return entityManager.createQuery(
                "SELECT COUNT(department) FROM Department department WHERE department.active = TRUE",
                Long.class).getSingleResult();
    }

    public List<Object[]> activeEmployeesByDepartment(List<Long> ids) {
        return entityManager.createQuery(
                "SELECT employee.department.id, COUNT(employee) FROM Employee employee"
                        + " WHERE employee.department.id IN :ids AND employee.status = :status"
                        + " GROUP BY employee.department.id", Object[].class)
                .setParameter("ids", ids).setParameter("status", EmployeeStatus.ACTIVE).getResultList();
    }

    public List<Object[]> nonTerminalTasksByDepartment(List<Long> ids) {
        return entityManager.createQuery(
                "SELECT task.department.id, COUNT(task) FROM Task task"
                        + " WHERE task.department.id IN :ids AND task.status IN :statuses"
                        + " GROUP BY task.department.id", Object[].class)
                .setParameter("ids", ids).setParameter(STATUSES_PARAMETER, NON_TERMINAL).getResultList();
    }

    public List<Object[]> overdueTasksByDepartment(List<Long> ids, LocalDateTime now) {
        return entityManager.createQuery(
                "SELECT task.department.id, COUNT(task) FROM Task task"
                        + " WHERE task.department.id IN :ids AND task.status IN :statuses"
                        + " AND task.dueAt IS NOT NULL AND task.dueAt < :now GROUP BY task.department.id",
                Object[].class).setParameter("ids", ids).setParameter(STATUSES_PARAMETER, NON_TERMINAL)
                .setParameter("now", now).getResultList();
    }

    private String taskScope(Long departmentId, Long employeeId) {
        return (departmentId == null ? "" : " AND task.department.id = :departmentId")
                + (employeeId == null ? "" : " AND task.assignedEmployee.id = :employeeId");
    }

    private void bindTaskScope(TypedQuery<?> query, Long departmentId, Long employeeId) {
        if (departmentId != null) query.setParameter("departmentId", departmentId);
        if (employeeId != null) query.setParameter("employeeId", employeeId);
    }
}

package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.Task;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * STUDY NOTE: A Repository is the data-access layer between services and persisted data.
 * Here, @Repository makes this class a Spring-managed persistence component and enables persistence-exception
 * translation.
 * AnalyticsService uses its EntityManager (JPA's database access object) for independent read-only counts
 * and grouped results.
 * JPQL queries use entity/field names, fixed application-controlled fragments and named parameters, never
 * interpolated request values.
 */
// Registers this persistence component and enables Spring persistence-exception translation.
@Repository
public class AnalyticsRepository {

    // Query study key: JPQL uses mapped Java entities; EntityManager executes it through Hibernate/MySQL.
    // setParameter binds each request-derived value separately from fixed query text.
    /**
     * Named JPQL parameter used for non-terminal status lists; values remain bound rather than interpolated.
     */
    private static final String STATUSES_PARAMETER = "statuses";
    /**
     * OPEN, ASSIGNED, and IN_PROGRESS Tasks count as active work for guards and aggregates.
     */
    private static final List<Task.Status> NON_TERMINAL = List.of(
            Task.Status.OPEN, Task.Status.ASSIGNED, Task.Status.IN_PROGRESS);
    /**
     * JPA query entry point for read-only aggregate counts and grouped projections.
     */
    private final EntityManager entityManager;

    /**
     * Receives the JPA EntityManager used for independent aggregate queries and named parameter binding.
     */
    public AnalyticsRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    /**
     * Counts retained User→Event join memberships, optionally for one User.
     * Fixed scope fragments use named parameters instead of interpolating IDs.
     */
    public long countRegistrations(Long userId) {
        // Only fixed query fragments are appended; user values are bound, never interpolated.
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(event) FROM User user JOIN user.registeredEvents event"
                        + (userId == null ? "" : " WHERE user.id = :userId"), Long.class);
        if (userId != null) query.setParameter("userId", userId);
        return query.getSingleResult();
    }

    /**
     * Groups active Rooms by status, optionally binding a floor filter.
     */
    public List<Object[]> roomCounts(Integer floor) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT room.status, COUNT(room) FROM Room room WHERE room.active = TRUE"
                        + (floor == null ? "" : " AND room.floor = :floor")
                        + " GROUP BY room.status", Object[].class);
        if (floor != null) query.setParameter("floor", floor);
        return query.getResultList();
    }

    /**
     * Groups all retained Tasks by status with optional Department and assignee scope.
     */
    public List<Object[]> taskCounts(Long departmentId, Long employeeId) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT task.status, COUNT(task) FROM Task task WHERE 1 = 1"
                        + taskScope(departmentId, employeeId) + " GROUP BY task.status", Object[].class);
        bindTaskScope(query, departmentId, employeeId);
        return query.getResultList();
    }

    /**
     * Counts OPEN, ASSIGNED, and IN_PROGRESS Tasks with a non-null due time strictly before the supplied time.
     */
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

    /**
     * Groups retained Alerts by status, optionally scoped to one recipient Employee.
     */
    public List<Object[]> alertCounts(Long employeeId) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT alert.status, COUNT(alert) FROM Alert alert"
                        + (employeeId == null ? "" : " WHERE alert.employee.id = :employeeId")
                        + " GROUP BY alert.status", Object[].class);
        if (employeeId != null) query.setParameter("employeeId", employeeId);
        return query.getResultList();
    }

    /**
     * Counts active stock rows in the optional Department scope, not the sum of units.
     */
    public long activeInventoryItemCount(Long departmentId) {
        return inventoryCount(departmentId, false);
    }

    /**
     * Counts active stock rows whose quantity is at or below their reorder threshold.
     */
    public long lowStockItemCount(Long departmentId) { return inventoryCount(departmentId, true); }

    /**
     * Builds an active-item count from fixed optional Department and low-stock fragments, binding the Department value.
     */
    private long inventoryCount(Long departmentId, boolean lowStock) {
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(item) FROM InventoryItem item WHERE item.active = TRUE"
                        + (departmentId == null ? "" : " AND item.department.id = :departmentId")
                        + (lowStock ? " AND item.quantity <= item.reorderThreshold" : ""), Long.class);
        if (departmentId != null) query.setParameter("departmentId", departmentId);
        return query.getSingleResult();
    }

    /**
     * Groups every retained Event by lifecycle status, including completed and cancelled history.
     */
    public List<Object[]> eventCounts() {
        return entityManager.createQuery(
                "SELECT event.status, COUNT(event) FROM Event event GROUP BY event.status",
                Object[].class).getResultList();
    }

    /**
     * Counts all retained Tasks with an Event reference, irrespective of Task or Event status.
     */
    public long eventTaskCount() {
        return entityManager.createQuery(
                "SELECT COUNT(task) FROM Task task WHERE task.event IS NOT NULL", Long.class)
                .getSingleResult();
    }

    /**
     * Counts Event-linked Tasks whose status is COMPLETED.
     */
    public long completedEventTaskCount() {
        return entityManager.createQuery(
                "SELECT COUNT(task) FROM Task task WHERE task.event IS NOT NULL AND task.status = :status",
                Long.class).setParameter("status", Task.Status.COMPLETED).getSingleResult();
    }

    /**
     * Counts Departments with active=true.
     */
    public long activeDepartmentCount() {
        return entityManager.createQuery(
                "SELECT COUNT(department) FROM Department department WHERE department.active = TRUE",
                Long.class).getSingleResult();
    }

    /**
     * Groups active Employee counts for only the selected Department IDs.
     */
    public List<Object[]> activeEmployeesByDepartment(List<Long> ids) {
        return entityManager.createQuery(
                "SELECT employee.department.id, COUNT(employee) FROM Employee employee"
                        + " WHERE employee.department.id IN :ids AND employee.status = :status"
                        + " GROUP BY employee.department.id", Object[].class)
                .setParameter("ids", ids).setParameter("status", Employee.Status.ACTIVE).getResultList();
    }

    /**
     * Groups OPEN, ASSIGNED, and IN_PROGRESS Task counts for the selected Department IDs.
     */
    public List<Object[]> nonTerminalTasksByDepartment(List<Long> ids) {
        return entityManager.createQuery(
                "SELECT task.department.id, COUNT(task) FROM Task task"
                        + " WHERE task.department.id IN :ids AND task.status IN :statuses"
                        + " GROUP BY task.department.id", Object[].class)
                .setParameter("ids", ids).setParameter(STATUSES_PARAMETER, NON_TERMINAL).getResultList();
    }

    /**
     * Groups non-terminal Tasks with dueAt strictly before the supplied time for selected Departments.
     */
    public List<Object[]> overdueTasksByDepartment(List<Long> ids, LocalDateTime now) {
        return entityManager.createQuery(
                "SELECT task.department.id, COUNT(task) FROM Task task"
                        + " WHERE task.department.id IN :ids AND task.status IN :statuses"
                        + " AND task.dueAt IS NOT NULL AND task.dueAt < :now GROUP BY task.department.id",
                Object[].class).setParameter("ids", ids).setParameter(STATUSES_PARAMETER, NON_TERMINAL)
                .setParameter("now", now).getResultList();
    }

    /**
     * Returns only fixed optional JPQL scope fragments; caller-supplied IDs never become query text.
     */
    private String taskScope(Long departmentId, Long employeeId) {
        return (departmentId == null ? "" : " AND task.department.id = :departmentId")
                + (employeeId == null ? "" : " AND task.assignedEmployee.id = :employeeId");
    }

    /**
     * Binds Department and Employee IDs only when their matching fixed clauses are present.
     */
    private void bindTaskScope(TypedQuery<?> query, Long departmentId, Long employeeId) {
        if (departmentId != null) query.setParameter("departmentId", departmentId);
        if (employeeId != null) query.setParameter("employeeId", employeeId);
    }
}

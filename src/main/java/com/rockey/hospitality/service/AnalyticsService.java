package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.AnalyticsDtos.*;
import com.rockey.hospitality.dto.CommonDtos.PagedResponse;
import com.rockey.hospitality.entity.*;
import com.rockey.hospitality.entity.Alert;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.Event;
import com.rockey.hospitality.entity.Room;
import com.rockey.hospitality.entity.Task;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.exception.ApiException.BadRequestException;
import com.rockey.hospitality.exception.ApiException.ForbiddenException;
import com.rockey.hospitality.exception.ApiException.ResourceNotFoundException;
import com.rockey.hospitality.repository.AnalyticsRepository;
import com.rockey.hospitality.repository.DepartmentRepository;
import com.rockey.hospitality.repository.EmployeeRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STUDY NOTE: A Service holds business rules and coordinates an application workflow.
 * Here, @Service lets Spring manage and inject this component; @Transactional groups database work so unchecked
 * failures roll back writes.
 * AnalyticsService returns read-only role-scoped counts, with no hotel operational data for USER.
 * AnalyticsController delegates here; Analytics and Department/Employee repositories provide the persisted
 * data through JPA/Hibernate.
 */
@Service
// Class-level: every public method runs in a read-only transaction, keeping lazy reads and DTO mapping inside the persistence boundary.
@Transactional(readOnly = true)
public class AnalyticsService {

    // Transaction study key: Spring applies @Transactional when another component calls this managed service.
    // readOnly=true requests a read-oriented transaction; it keeps lazy reads and DTO mapping inside the
    // persistence boundary.
    // readOnly is not an authorization rule; repositories still run only after the service's scope checks.
    /**
     * Injected AnalyticsRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final AnalyticsRepository analytics;
    /**
     * Injected EmployeeRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final EmployeeRepository employees;
    /**
     * Injected DepartmentRepository used to page Department rows for workload analytics.
     */
    private final DepartmentRepository departments;
    /**
     * Injected Clock used to derive the server-zone asOf snapshot for counts.
     */
    private final Clock clock;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public AnalyticsService(AnalyticsRepository analytics, EmployeeRepository employees,
            DepartmentRepository departments, Clock clock) {
        this.analytics = analytics;
        this.employees = employees;
        this.departments = departments;
        this.clock = clock;
    }

    /**
     * Selects only the authenticated role's response section.
     * STAFF counts use its own Employee and Department; ADMIN counts use global queries.
     */
    public DashboardResponse dashboard(Long userId, User.Role role) {
        // Aggregate only the permitted role/identity scope; React must never hide leaked global data.
        if (role == null || userId == null || userId <= 0) {
            throw new ForbiddenException("Dashboard requires an authenticated identity.");
        }
        OffsetDateTime asOf = asOf();
        if (role == User.Role.USER) {
            return new DashboardResponse(role, asOf,
                    new DashboardResponse.UserDashboard(analytics.countRegistrations(userId)), null, null);
        }
        if (role == User.Role.STAFF) {
            Employee employee = employees.findByUserId(userId)
                    .orElseThrow(() -> new ForbiddenException("An active employee profile is required."));
            if (employee.getStatus() != Employee.Status.ACTIVE
                    || employee.getDepartment() == null
                    || !Boolean.TRUE.equals(employee.getDepartment().getActive())) {
                throw new ForbiddenException("An active employee profile is required.");
            }
            Long employeeId = employee.getId();
            Long departmentId = employee.getDepartment().getId();
            Map<Task.Status, Long> tasks = enumCounts(Task.Status.class, analytics.taskCounts(null, employeeId));
            Map<Alert.Status, Long> alerts = enumCounts(Alert.Status.class, analytics.alertCounts(employeeId));
            return new DashboardResponse(role, asOf, null, new DashboardResponse.StaffDashboard(
                    employeeId, departmentId, nonTerminalTasks(tasks),
                    analytics.overdueTaskCount(null, employeeId, asOf.toLocalDateTime()),
                    alerts.get(Alert.Status.UNREAD), unresolvedAlerts(alerts),
                    analytics.activeInventoryItemCount(departmentId), analytics.lowStockItemCount(departmentId)), null);
        }
        requireAdmin(role);
        Map<Room.Status, Long> rooms = enumCounts(Room.Status.class, analytics.roomCounts(null));
        Map<Task.Status, Long> tasks = enumCounts(Task.Status.class, analytics.taskCounts(null, null));
        Map<Alert.Status, Long> alerts = enumCounts(Alert.Status.class, analytics.alertCounts(null));
        Map<Event.Status, Long> events = enumCounts(Event.Status.class, analytics.eventCounts());
        return new DashboardResponse(role, asOf, null, null, new DashboardResponse.AdminDashboard(
                total(rooms), rooms.get(Room.Status.READY), nonTerminalTasks(tasks),
                analytics.overdueTaskCount(null, null, asOf.toLocalDateTime()), tasks.get(Task.Status.COMPLETED),
                analytics.activeDepartmentCount(), unresolvedAlerts(alerts),
                analytics.activeInventoryItemCount(null), analytics.lowStockItemCount(null),
                events.get(Event.Status.DRAFT) + events.get(Event.Status.OPEN)
                        + events.get(Event.Status.CLOSED) + events.get(Event.Status.IN_PROGRESS),
                analytics.countRegistrations(null)));
    }

    /**
     * Returns ADMIN-only active Room counts by status with an optional validated floor filter.
     */
    public RoomAnalyticsResponse rooms(Integer floor, User.Role role) {
        requireAdmin(role);
        if (floor != null && (floor < 1 || floor > 99)) {
            throw new BadRequestException("Floor must be between 1 and 99.");
        }
        OffsetDateTime asOf = asOf();
        Map<Room.Status, Long> counts = enumCounts(Room.Status.class, analytics.roomCounts(floor));
        return new RoomAnalyticsResponse(asOf, floor, total(counts), counts);
    }

    /**
     * Returns ADMIN-only Task counts by status and strict overdue counts, optionally scoped to a Department.
     */
    public TaskAnalyticsResponse tasks(Long departmentId, User.Role role) {
        requireAdmin(role);
        validateDepartmentId(departmentId);
        OffsetDateTime asOf = asOf();
        Map<Task.Status, Long> counts = enumCounts(Task.Status.class, analytics.taskCounts(departmentId, null));
        return new TaskAnalyticsResponse(asOf, departmentId, total(counts), counts,
                analytics.overdueTaskCount(departmentId, null, asOf.toLocalDateTime()));
    }

    /**
     * Paginates Department workload summaries in ID order, including inactive Department rows.
     * A requested missing Department returns 404; far-out pages remain safely empty.
     */
    public DepartmentAnalyticsResponse departments(Long departmentId, int page, int size, User.Role role) {
        requireAdmin(role);
        validateDepartmentId(departmentId);
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Page must be non-negative; size must be between 1 and 100.");
        }
        OffsetDateTime asOf = asOf();
        List<Department> rows;
        long totalElements;
        if (departmentId != null) {
            Department department = departments.findById(departmentId).orElseThrow(() ->
                    new ResourceNotFoundException("Department not found with id " + departmentId + "."));
            totalElements = 1;
            rows = page == 0 ? List.of(department) : List.of();
        } else {
            totalElements = departments.count();
            // Avoid overflowing a JPA integer offset for a valid, far-out empty page.
            rows = (long) page * size >= totalElements ? List.of()
                    : departments.findAll(PageRequest.of(page, size, Sort.by("id").ascending())).getContent();
        }
        List<DepartmentWorkloadSummary> content = departmentWorkload(rows, asOf.toLocalDateTime());
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new DepartmentAnalyticsResponse(asOf, new PagedResponse<>(content, page, size,
                totalElements, totalPages, page >= totalPages - 1));
    }

    /**
     * Combines active Inventory threshold counts with global Event registration and preparation counts.
     * The Department filter applies only to Inventory.
     */
    public OperationsAnalyticsResponse operations(Long departmentId, User.Role role) {
        requireAdmin(role);
        validateDepartmentId(departmentId);
        OffsetDateTime asOf = asOf();
        Map<Event.Status, Long> events = enumCounts(Event.Status.class, analytics.eventCounts());
        return new OperationsAnalyticsResponse(asOf, new OperationsAnalyticsResponse.InventoryCounts(
                departmentId, analytics.activeInventoryItemCount(departmentId), analytics.lowStockItemCount(departmentId)),
                new OperationsAnalyticsResponse.EventCounts(total(events), events,
                        analytics.countRegistrations(null), analytics.eventTaskCount(), analytics.completedEventTaskCount()));
    }

    /**
     * Combines three independent grouped queries with the selected Department page.
     * Missing aggregate rows become zero counts rather than omitted Departments.
     */
    private List<DepartmentWorkloadSummary> departmentWorkload(List<Department> rows, LocalDateTime now) {
        if (rows.isEmpty()) return List.of();
        List<Long> ids = rows.stream().map(Department::getId).toList();
        Map<Long, Long> employeeCounts = idCounts(analytics.activeEmployeesByDepartment(ids));
        Map<Long, Long> taskCounts = idCounts(analytics.nonTerminalTasksByDepartment(ids));
        Map<Long, Long> overdueCounts = idCounts(analytics.overdueTasksByDepartment(ids, now));
        return rows.stream().map(department -> new DepartmentWorkloadSummary(department.getId(),
                department.getName(), Boolean.TRUE.equals(department.getActive()),
                employeeCounts.getOrDefault(department.getId(), 0L),
                taskCounts.getOrDefault(department.getId(), 0L),
                overdueCounts.getOrDefault(department.getId(), 0L))).toList();
    }

    /**
     * Returns an offset timestamp from the injected Clock in the server's zone, matching operational LocalDateTime comparisons.
     */
    private OffsetDateTime asOf() { return OffsetDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()); }

    /**
     * Enforces ADMIN-only analytics in the service as well as at the route boundary.
     */
    private void requireAdmin(User.Role role) {
        if (role != User.Role.ADMIN) throw new ForbiddenException("Analytics requires ADMIN access.");
    }

    /**
     * Rejects a supplied zero or negative Department filter without requiring an existing row for count-only queries.
     */
    private void validateDepartmentId(Long departmentId) {
        if (departmentId != null && departmentId <= 0) throw new BadRequestException("Department filter must be positive.");
    }

    /**
     * Initializes every enum status to zero before overlaying database groups, keeping empty-data response shapes stable.
     */
    private <E extends Enum<E>> Map<E, Long> enumCounts(Class<E> type, List<Object[]> rows) {
        Map<E, Long> counts = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) counts.put(value, 0L);
        for (Object[] row : rows) counts.put(type.cast(row[0]), ((Number) row[1]).longValue());
        return counts;
    }

    /**
     * Converts grouped Department ID/count rows into a map for workload assembly.
     */
    private Map<Long, Long> idCounts(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        return counts;
    }

    /**
     * Sums the independently computed counts into the response total.
     */
    private long total(Map<?, Long> counts) { return counts.values().stream().mapToLong(Long::longValue).sum(); }
    /**
     * Adds OPEN, ASSIGNED, and IN_PROGRESS counts; completed and cancelled work is excluded.
     */
    private long nonTerminalTasks(Map<Task.Status, Long> counts) {
        return counts.get(Task.Status.OPEN) + counts.get(Task.Status.ASSIGNED) + counts.get(Task.Status.IN_PROGRESS);
    }
    /**
     * Adds UNREAD and READ alerts; resolved history is excluded.
     */
    private long unresolvedAlerts(Map<Alert.Status, Long> counts) {
        return counts.get(Alert.Status.UNREAD) + counts.get(Alert.Status.READ);
    }
}

package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.analytics.*;
import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.entity.*;
import com.rockey.hospitality.exception.BadRequestException;
import com.rockey.hospitality.exception.ForbiddenException;
import com.rockey.hospitality.exception.ResourceNotFoundException;
import com.rockey.hospitality.repository.AnalyticsRepository;
import com.rockey.hospitality.repository.DepartmentRepository;
import com.rockey.hospitality.repository.EmployeeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    private final AnalyticsRepository analytics;
    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final Clock clock;

    public AnalyticsService(AnalyticsRepository analytics, EmployeeRepository employees,
            DepartmentRepository departments, Clock clock) {
        this.analytics = analytics;
        this.employees = employees;
        this.departments = departments;
        this.clock = clock;
    }

    public DashboardResponse dashboard(Long userId, Role role) {
        if (role == null || userId == null || userId <= 0) {
            throw new ForbiddenException("Dashboard requires an authenticated identity.");
        }
        OffsetDateTime asOf = asOf();
        if (role == Role.USER) {
            return new DashboardResponse(role, asOf,
                    new DashboardResponse.UserDashboard(analytics.countRegistrations(userId)), null, null);
        }
        if (role == Role.STAFF) {
            Employee employee = employees.findByUserId(userId)
                    .orElseThrow(() -> new ForbiddenException("An active employee profile is required."));
            if (employee.getStatus() != EmployeeStatus.ACTIVE
                    || employee.getDepartment() == null
                    || !Boolean.TRUE.equals(employee.getDepartment().getActive())) {
                throw new ForbiddenException("An active employee profile is required.");
            }
            Long employeeId = employee.getId();
            Long departmentId = employee.getDepartment().getId();
            Map<TaskStatus, Long> tasks = enumCounts(TaskStatus.class, analytics.taskCounts(null, employeeId));
            Map<AlertStatus, Long> alerts = enumCounts(AlertStatus.class, analytics.alertCounts(employeeId));
            return new DashboardResponse(role, asOf, null, new DashboardResponse.StaffDashboard(
                    employeeId, departmentId, nonTerminalTasks(tasks),
                    analytics.overdueTaskCount(null, employeeId, asOf.toLocalDateTime()),
                    alerts.get(AlertStatus.UNREAD), unresolvedAlerts(alerts),
                    analytics.activeInventoryItemCount(departmentId), analytics.lowStockItemCount(departmentId)), null);
        }
        requireAdmin(role);
        Map<RoomStatus, Long> rooms = enumCounts(RoomStatus.class, analytics.roomCounts(null));
        Map<TaskStatus, Long> tasks = enumCounts(TaskStatus.class, analytics.taskCounts(null, null));
        Map<AlertStatus, Long> alerts = enumCounts(AlertStatus.class, analytics.alertCounts(null));
        Map<EventStatus, Long> events = enumCounts(EventStatus.class, analytics.eventCounts());
        return new DashboardResponse(role, asOf, null, null, new DashboardResponse.AdminDashboard(
                total(rooms), rooms.get(RoomStatus.READY), nonTerminalTasks(tasks),
                analytics.overdueTaskCount(null, null, asOf.toLocalDateTime()), tasks.get(TaskStatus.COMPLETED),
                analytics.activeDepartmentCount(), unresolvedAlerts(alerts),
                analytics.activeInventoryItemCount(null), analytics.lowStockItemCount(null),
                events.get(EventStatus.DRAFT) + events.get(EventStatus.OPEN)
                        + events.get(EventStatus.CLOSED) + events.get(EventStatus.IN_PROGRESS),
                analytics.countRegistrations(null)));
    }

    public RoomAnalyticsResponse rooms(Integer floor, Role role) {
        requireAdmin(role);
        if (floor != null && (floor < 1 || floor > 99)) {
            throw new BadRequestException("Floor must be between 1 and 99.");
        }
        OffsetDateTime asOf = asOf();
        Map<RoomStatus, Long> counts = enumCounts(RoomStatus.class, analytics.roomCounts(floor));
        return new RoomAnalyticsResponse(asOf, floor, total(counts), counts);
    }

    public TaskAnalyticsResponse tasks(Long departmentId, Role role) {
        requireAdmin(role);
        validateDepartmentId(departmentId);
        OffsetDateTime asOf = asOf();
        Map<TaskStatus, Long> counts = enumCounts(TaskStatus.class, analytics.taskCounts(departmentId, null));
        return new TaskAnalyticsResponse(asOf, departmentId, total(counts), counts,
                analytics.overdueTaskCount(departmentId, null, asOf.toLocalDateTime()));
    }

    public DepartmentAnalyticsResponse departments(Long departmentId, int page, int size, Role role) {
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

    public OperationsAnalyticsResponse operations(Long departmentId, Role role) {
        requireAdmin(role);
        validateDepartmentId(departmentId);
        OffsetDateTime asOf = asOf();
        Map<EventStatus, Long> events = enumCounts(EventStatus.class, analytics.eventCounts());
        return new OperationsAnalyticsResponse(asOf, new OperationsAnalyticsResponse.InventoryCounts(
                departmentId, analytics.activeInventoryItemCount(departmentId), analytics.lowStockItemCount(departmentId)),
                new OperationsAnalyticsResponse.EventCounts(total(events), events,
                        analytics.countRegistrations(null), analytics.eventTaskCount(), analytics.completedEventTaskCount()));
    }

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

    private OffsetDateTime asOf() { return OffsetDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()); }

    private void requireAdmin(Role role) {
        if (role != Role.ADMIN) throw new ForbiddenException("Analytics requires ADMIN access.");
    }

    private void validateDepartmentId(Long departmentId) {
        if (departmentId != null && departmentId <= 0) throw new BadRequestException("Department filter must be positive.");
    }

    private <E extends Enum<E>> Map<E, Long> enumCounts(Class<E> type, List<Object[]> rows) {
        Map<E, Long> counts = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) counts.put(value, 0L);
        for (Object[] row : rows) counts.put(type.cast(row[0]), ((Number) row[1]).longValue());
        return counts;
    }

    private Map<Long, Long> idCounts(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        return counts;
    }

    private long total(Map<?, Long> counts) { return counts.values().stream().mapToLong(Long::longValue).sum(); }
    private long nonTerminalTasks(Map<TaskStatus, Long> counts) {
        return counts.get(TaskStatus.OPEN) + counts.get(TaskStatus.ASSIGNED) + counts.get(TaskStatus.IN_PROGRESS);
    }
    private long unresolvedAlerts(Map<AlertStatus, Long> counts) {
        return counts.get(AlertStatus.UNREAD) + counts.get(AlertStatus.READ);
    }
}

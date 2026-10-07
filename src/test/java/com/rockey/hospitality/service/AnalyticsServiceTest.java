package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.AnalyticsDtos.*;
import com.rockey.hospitality.entity.*;
import com.rockey.hospitality.entity.Alert;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.Event;
import com.rockey.hospitality.entity.Room;
import com.rockey.hospitality.entity.Task;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.exception.*;
import com.rockey.hospitality.exception.ApiException.BadRequestException;
import com.rockey.hospitality.exception.ApiException.ForbiddenException;
import com.rockey.hospitality.exception.ApiException.ResourceNotFoundException;
import com.rockey.hospitality.repository.AnalyticsRepository;
import com.rockey.hospitality.repository.DepartmentRepository;
import com.rockey.hospitality.repository.EmployeeRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.ofInstant(NOW, ZoneId.systemDefault());
    @Mock private AnalyticsRepository analytics;
    @Mock private EmployeeRepository employees;
    @Mock private DepartmentRepository departments;
    private AnalyticsService service;

    @BeforeEach void setUp() {
        service = new AnalyticsService(analytics, employees, departments, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void userDashboardQueriesOnlyOwnRetainedRegistrations() {
        when(analytics.countRegistrations(31L)).thenReturn(4L);
        DashboardResponse result = service.dashboard(31L, User.Role.USER);
        assertEquals(4, result.getUser().getRegistrationCount());
        assertEquals(User.Role.USER, result.getRole());
        assertNull(result.getStaff()); assertNull(result.getAdmin());
        verify(analytics).countRegistrations(31L);
        verifyNoMoreInteractions(analytics); verifyNoInteractions(employees, departments);
    }

    @Test void missingUserIdentityCannotBecomeGlobalRegistrationScope() {
        assertThrows(ForbiddenException.class, () -> service.dashboard(null, User.Role.USER));
        assertThrows(ForbiddenException.class, () -> service.dashboard(0L, User.Role.USER));
        assertThrows(ForbiddenException.class, () -> service.dashboard(31L, null));
        verifyNoInteractions(analytics, employees, departments);
    }

    @Test void staffCountsAreIdentityAndDepartmentScopedWithTerminalWorkExcluded() {
        when(employees.findByUserId(21L)).thenReturn(Optional.of(employee()));
        when(analytics.taskCounts(null, 12L)).thenReturn(List.of(
                row(Task.Status.OPEN, 1), row(Task.Status.ASSIGNED, 2), row(Task.Status.IN_PROGRESS, 3),
                row(Task.Status.COMPLETED, 40), row(Task.Status.CANCELLED, 50)));
        when(analytics.overdueTaskCount(null, 12L, LOCAL_NOW)).thenReturn(2L);
        when(analytics.alertCounts(12L)).thenReturn(List.of(
                row(Alert.Status.UNREAD, 4), row(Alert.Status.READ, 5), row(Alert.Status.RESOLVED, 60)));
        when(analytics.activeInventoryItemCount(3L)).thenReturn(7L);
        when(analytics.lowStockItemCount(3L)).thenReturn(2L);
        DashboardResponse result = service.dashboard(21L, User.Role.STAFF);
        var staff = result.getStaff();
        assertEquals(12L, staff.getEmployeeId()); assertEquals(3L, staff.getDepartmentId());
        assertEquals(6, staff.getNonTerminalAssignedTaskCount()); assertEquals(2, staff.getOverdueAssignedTaskCount());
        assertEquals(4, staff.getUnreadAlertCount()); assertEquals(9, staff.getUnresolvedAlertCount());
        assertEquals(7, staff.getActiveInventoryItemCount()); assertEquals(2, staff.getLowStockItemCount());
        assertNull(result.getUser()); assertNull(result.getAdmin());
        verify(analytics).taskCounts(null, 12L); verify(analytics).overdueTaskCount(null, 12L, LOCAL_NOW);
        verify(analytics).alertCounts(12L); verify(analytics).activeInventoryItemCount(3L);
        verify(analytics).lowStockItemCount(3L); verifyNoMoreInteractions(analytics);
    }

    @ParameterizedTest @ValueSource(strings = {"missingEmployee", "inactiveEmployee", "inactiveDepartment", "missingDepartment"})
    void staffEligibilityFailsBeforeAnyAggregateQuery(String condition) {
        Employee employee = employee();
        if (condition.equals("inactiveEmployee")) ReflectionTestUtils.setField(employee, "status", Employee.Status.INACTIVE);
        if (condition.equals("inactiveDepartment")) employee.getDepartment().deactivate();
        if (condition.equals("missingDepartment")) ReflectionTestUtils.setField(employee, "department", null);
        when(employees.findByUserId(21L)).thenReturn(condition.equals("missingEmployee") ? Optional.empty() : Optional.of(employee));
        assertThrows(ForbiddenException.class, () -> service.dashboard(21L, User.Role.STAFF));
        verifyNoInteractions(analytics);
    }

    @Test void adminDashboardUsesApprovedGlobalDefinitions() {
        when(analytics.roomCounts(null)).thenReturn(List.of(row(Room.Status.READY, 3), row(Room.Status.DIRTY, 2)));
        when(analytics.taskCounts(null, null)).thenReturn(List.of(row(Task.Status.OPEN, 2), row(Task.Status.ASSIGNED, 3),
                row(Task.Status.IN_PROGRESS, 4), row(Task.Status.COMPLETED, 5), row(Task.Status.CANCELLED, 6)));
        when(analytics.overdueTaskCount(null, null, LOCAL_NOW)).thenReturn(1L);
        when(analytics.alertCounts(null)).thenReturn(List.of(row(Alert.Status.UNREAD, 7), row(Alert.Status.READ, 8), row(Alert.Status.RESOLVED, 90)));
        when(analytics.eventCounts()).thenReturn(List.of(row(Event.Status.DRAFT, 1), row(Event.Status.OPEN, 2),
                row(Event.Status.CLOSED, 3), row(Event.Status.IN_PROGRESS, 4), row(Event.Status.COMPLETED, 5), row(Event.Status.CANCELLED, 6)));
        when(analytics.activeDepartmentCount()).thenReturn(4L);
        when(analytics.activeInventoryItemCount(null)).thenReturn(12L);
        when(analytics.lowStockItemCount(null)).thenReturn(3L);
        when(analytics.countRegistrations(null)).thenReturn(22L);
        var result = service.dashboard(1L, User.Role.ADMIN).getAdmin();
        assertEquals(5, result.getActiveRoomCount()); assertEquals(3, result.getReadyRoomCount());
        assertEquals(9, result.getNonTerminalTaskCount()); assertEquals(1, result.getOverdueTaskCount());
        assertEquals(5, result.getCompletedTaskCount()); assertEquals(4, result.getActiveDepartmentCount());
        assertEquals(15, result.getUnresolvedAlertCount()); assertEquals(12, result.getActiveInventoryItemCount());
        assertEquals(3, result.getLowStockItemCount()); assertEquals(10, result.getNonTerminalEventCount());
        assertEquals(22, result.getRegistrationCount()); verifyNoInteractions(employees, departments);
    }

    @Test void roomCountsIncludeEveryCanonicalStatusAndReconcile() {
        List<Object[]> fixture = new ArrayList<>();
        long expected = 0;
        for (Room.Status status : Room.Status.values()) { long count = status.ordinal() + 1L; fixture.add(row(status, count)); expected += count; }
        when(analytics.roomCounts(2)).thenReturn(fixture);
        var result = service.rooms(2, User.Role.ADMIN);
        assertEquals(2, result.getFloor()); assertEquals(expected, result.getActiveRoomCount());
        for (Room.Status status : Room.Status.values()) assertEquals(status.ordinal() + 1L, result.getRoomCountsByStatus().get(status));
        var statusCounts = result.getRoomCountsByStatus();
        assertThrows(UnsupportedOperationException.class, () -> statusCounts.put(Room.Status.READY, 0L));
    }

    @Test void tasksIncludeCompletedCancelledAndReturnIndependentOverdueSubset() {
        when(analytics.taskCounts(3L, null)).thenReturn(List.of(row(Task.Status.OPEN, 1), row(Task.Status.ASSIGNED, 2),
                row(Task.Status.IN_PROGRESS, 3), row(Task.Status.COMPLETED, 4), row(Task.Status.CANCELLED, 5)));
        when(analytics.overdueTaskCount(3L, null, LOCAL_NOW)).thenReturn(2L);
        var result = service.tasks(3L, User.Role.ADMIN);
        assertEquals(3L, result.getDepartmentId()); assertEquals(15, result.getTotalTaskCount());
        assertEquals(4, result.getTaskCountsByStatus().get(Task.Status.COMPLETED));
        assertEquals(5, result.getTaskCountsByStatus().get(Task.Status.CANCELLED)); assertEquals(2, result.getOverdueTaskCount());
    }

    @Test void inventoryFilterDoesNotFilterEventHistoryRegistrationsOrPreparation() {
        when(analytics.eventCounts()).thenReturn(List.of(row(Event.Status.CANCELLED, 2), row(Event.Status.COMPLETED, 3)));
        when(analytics.countRegistrations(null)).thenReturn(11L);
        when(analytics.eventTaskCount()).thenReturn(8L);
        when(analytics.completedEventTaskCount()).thenReturn(5L);
        when(analytics.activeInventoryItemCount(999L)).thenReturn(0L);
        var result = service.operations(999L, User.Role.ADMIN);
        assertEquals(999L, result.getInventory().getDepartmentId());
        assertEquals(0, result.getInventory().getActiveInventoryItemCount());
        assertEquals(5, result.getEvents().getEventCount()); assertEquals(11, result.getEvents().getRegistrationCount());
        assertEquals(8, result.getEvents().getEventTaskCount()); assertEquals(5, result.getEvents().getCompletedEventTaskCount());
        assertEquals(6, result.getEvents().getEventCountsByStatus().size()); verifyNoInteractions(departments);
    }

    @Test void eventWithoutTasksReportsZeroPreparationNotPercentage() {
        when(analytics.eventCounts()).thenReturn(Collections.singletonList(row(Event.Status.OPEN, 1)));
        var result = service.operations(null, User.Role.ADMIN);
        assertEquals(1, result.getEvents().getEventCount());
        assertEquals(0, result.getEvents().getEventTaskCount()); assertEquals(0, result.getEvents().getCompletedEventTaskCount());
    }

    @Test void emptyDataHasZeroCountsCompleteMapsAndEmptyPage() {
        var rooms = service.rooms(null, User.Role.ADMIN);
        var tasks = service.tasks(null, User.Role.ADMIN);
        var operations = service.operations(null, User.Role.ADMIN);
        var departmentPage = service.departments(null, 0, 20, User.Role.ADMIN).getDepartments();
        assertEquals(0, rooms.getActiveRoomCount()); assertEquals(7, rooms.getRoomCountsByStatus().size());
        assertTrue(rooms.getRoomCountsByStatus().values().stream().allMatch(n -> n == 0));
        assertEquals(0, tasks.getTotalTaskCount()); assertEquals(5, tasks.getTaskCountsByStatus().size());
        assertTrue(tasks.getTaskCountsByStatus().values().stream().allMatch(n -> n == 0));
        assertEquals(0, tasks.getOverdueTaskCount()); assertEquals(0, operations.getInventory().getLowStockItemCount());
        assertEquals(6, operations.getEvents().getEventCountsByStatus().size());
        assertTrue(operations.getEvents().getEventCountsByStatus().values().stream().allMatch(n -> n == 0));
        assertEquals(0, operations.getEvents().getRegistrationCount()); assertTrue(departmentPage.getContent().isEmpty());
        assertEquals(0, departmentPage.getTotalPages()); assertTrue(departmentPage.isLast());
        assertEquals(0, service.dashboard(31L, User.Role.USER).getUser().getRegistrationCount());
        assertEquals(0, service.dashboard(1L, User.Role.ADMIN).getAdmin().getNonTerminalTaskCount());
    }

    @Test void validStaffWithNoWorkHasZeroCounts() {
        when(employees.findByUserId(21L)).thenReturn(Optional.of(employee()));
        var result = service.dashboard(21L, User.Role.STAFF).getStaff();
        assertEquals(0, result.getNonTerminalAssignedTaskCount()); assertEquals(0, result.getOverdueAssignedTaskCount());
        assertEquals(0, result.getUnreadAlertCount()); assertEquals(0, result.getUnresolvedAlertCount());
        assertEquals(0, result.getActiveInventoryItemCount()); assertEquals(0, result.getLowStockItemCount());
    }

    @Test void inactiveDepartmentRemainsVisibleAndBatchCountsUseTaskDepartment() {
        Department first = department(3L); Department second = department(7L); second.deactivate();
        when(departments.count()).thenReturn(2L);
        when(departments.findAll(PageRequest.of(0, 20, Sort.by("id").ascending())))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(analytics.activeEmployeesByDepartment(List.of(3L, 7L))).thenReturn(Collections.singletonList(row(3L, 2)));
        when(analytics.nonTerminalTasksByDepartment(List.of(3L, 7L))).thenReturn(Collections.singletonList(row(3L, 5)));
        when(analytics.overdueTasksByDepartment(List.of(3L, 7L), LOCAL_NOW)).thenReturn(Collections.singletonList(row(3L, 1)));
        var page = service.departments(null, 0, 20, User.Role.ADMIN).getDepartments();
        assertEquals(2, page.getTotalElements()); assertEquals(1, page.getTotalPages()); assertEquals(20, page.getSize());
        var active = page.getContent().get(0); assertEquals(2, active.getActiveEmployeeCount());
        assertEquals(5, active.getNonTerminalTaskCount()); assertEquals(1, active.getOverdueTaskCount());
        var inactive = page.getContent().get(1); assertFalse(inactive.getActive());
        assertEquals(0, inactive.getActiveEmployeeCount()); assertEquals(0, inactive.getNonTerminalTaskCount());
    }

    @Test void requestedExistingEmptyDepartmentHasZeroSummary() {
        when(departments.findById(7L)).thenReturn(Optional.of(department(7L)));
        var page = service.departments(7L, 0, 20, User.Role.ADMIN).getDepartments();
        assertEquals(1, page.getTotalElements()); assertEquals(1, page.getContent().size());
        assertEquals(0, page.getContent().get(0).getActiveEmployeeCount());
    }

    @Test void nonexistentDepartmentSummaryUses404() {
        assertThrows(ResourceNotFoundException.class, () -> service.departments(999L, 0, 20, User.Role.ADMIN));
        verifyNoInteractions(analytics);
    }

    @Test void unknownDepartmentTaskFilterReturnsZeroWithoutExistenceLookup() {
        assertEquals(0, service.tasks(999L, User.Role.ADMIN).getTotalTaskCount()); verifyNoInteractions(departments);
    }

    @Test void paginationPreservesTotalsAndSkipsQueriesForFarOutEmptyPage() {
        when(departments.count()).thenReturn(21L);
        var page = service.departments(null, Integer.MAX_VALUE, 100, User.Role.ADMIN).getDepartments();
        assertEquals(21, page.getTotalElements()); assertEquals(1, page.getTotalPages());
        assertTrue(page.getContent().isEmpty()); assertTrue(page.isLast()); verifyNoInteractions(analytics);
    }

    @Test void filteredDepartmentOutsideFirstPageIsEmptyButNotMissing() {
        when(departments.findById(3L)).thenReturn(Optional.of(department(3L)));
        var page = service.departments(3L, 1, 20, User.Role.ADMIN).getDepartments();
        assertTrue(page.getContent().isEmpty()); assertEquals(1, page.getTotalElements()); verifyNoInteractions(analytics);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 100})
    void invalidFloorRejected(int floor) {
        assertThrows(BadRequestException.class, () -> service.rooms(floor, User.Role.ADMIN)); verifyNoInteractions(analytics);
    }

    @ParameterizedTest @ValueSource(ints = {1, 99})
    void inclusiveFloorBoundariesAccepted(int floor) { assertEquals(floor, service.rooms(floor, User.Role.ADMIN).getFloor()); }

    @ParameterizedTest @ValueSource(longs = {-1, 0})
    void invalidDepartmentFiltersRejected(long id) {
        assertThrows(BadRequestException.class, () -> service.tasks(id, User.Role.ADMIN));
        assertThrows(BadRequestException.class, () -> service.operations(id, User.Role.ADMIN));
        assertThrows(BadRequestException.class, () -> service.departments(id, 0, 20, User.Role.ADMIN)); verifyNoInteractions(analytics);
    }

    @ParameterizedTest @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    void invalidPaginationRejected(int page, int size) {
        assertThrows(BadRequestException.class, () -> service.departments(null, page, size, User.Role.ADMIN));
        verifyNoInteractions(analytics, departments);
    }

    @ParameterizedTest @ValueSource(strings = {"USER", "STAFF"})
    void nonAdminCannotCallAnyOperationalAnalyticsService(String name) {
        User.Role role = User.Role.valueOf(name);
        assertThrows(ForbiddenException.class, () -> service.rooms(null, role));
        assertThrows(ForbiddenException.class, () -> service.tasks(null, role));
        assertThrows(ForbiddenException.class, () -> service.departments(null, 0, 20, role));
        assertThrows(ForbiddenException.class, () -> service.operations(null, role)); verifyNoInteractions(analytics, departments);
    }

    @Test void requestCapturesOneInstantWithServerZoneAndReadonlyTransaction() {
        Clock changingClock = mock(Clock.class);
        when(changingClock.instant()).thenReturn(NOW, NOW.plusSeconds(3600));
        service = new AnalyticsService(analytics, employees, departments, changingClock);
        var response = service.tasks(null, User.Role.ADMIN);
        assertEquals(OffsetDateTime.ofInstant(NOW, ZoneId.systemDefault()), response.getAsOf());
        verify(changingClock, times(1)).instant(); verify(analytics).overdueTaskCount(null, null, LOCAL_NOW);
        assertTrue(AnalyticsService.class.getAnnotation(Transactional.class).readOnly());
    }

    private Object[] row(Object key, long count) { return new Object[]{key, count}; }
    private Department department(Long id) {
        Department department = new Department("Department " + id, "Operations");
        ReflectionTestUtils.setField(department, "id", id); return department;
    }
    private Employee employee() {
        Employee employee = new Employee("Worker", "worker@example.test", department(3L), "Staff", null);
        ReflectionTestUtils.setField(employee, "id", 12L); return employee;
    }
}

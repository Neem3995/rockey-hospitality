package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.*;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.Task;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** HQL is compiled against real entity mappings offline; result execution remains mocked. */
@ExtendWith(MockitoExtension.class)
class AnalyticsRepositoryTest {
    private static SessionFactory mappings;
    @Mock private EntityManager entityManager;
    private EntityManager parser;
    private TypedQuery<Long> countQuery;
    private TypedQuery<Object[]> groupQuery;
    private AnalyticsRepository repository;
    private String hql;
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 12, 0);
    private static final List<Task.Status> NON_TERMINAL = List.of(Task.Status.OPEN, Task.Status.ASSIGNED, Task.Status.IN_PROGRESS);

    @BeforeAll static void compileMappingsWithoutDatabase() {
        Configuration configuration = new Configuration()
                .setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .setProperty("hibernate.boot.allow_jdbc_metadata_access", "false")
                .setProperty("hibernate.hbm2ddl.auto", "none")
                .setProperty("hibernate.connection.provider_class",
                        "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl");
        for (Class<?> entity : List.of(User.class, Employee.class, Department.class, Room.class,
                Task.class, Event.class, InventoryItem.class, Alert.class)) configuration.addAnnotatedClass(entity);
        mappings = configuration.buildSessionFactory();
    }

    @AfterAll static void closeMappings() { if (mappings != null) mappings.close(); }

    @BeforeEach @SuppressWarnings("unchecked") void setUp() {
        parser = mappings.createEntityManager();
        countQuery = mock(TypedQuery.class, RETURNS_SELF);
        groupQuery = mock(TypedQuery.class, RETURNS_SELF);
        lenient().when(countQuery.getSingleResult()).thenReturn(3L);
        lenient().when(entityManager.createQuery(anyString(), eq(Long.class))).thenAnswer(call -> {
            hql = call.getArgument(0); parser.createQuery(hql, Long.class); return countQuery;
        });
        lenient().when(entityManager.createQuery(anyString(), eq(Object[].class))).thenAnswer(call -> {
            hql = call.getArgument(0); parser.createQuery(hql, Object[].class); return groupQuery;
        });
        repository = new AnalyticsRepository(entityManager);
    }

    @AfterEach void closeParser() { parser.close(); }

    @Test void ownRegistrationCountsJoinRowsNotDistinctEventsGlobally() {
        assertEquals(3, repository.countRegistrations(31L));
        assertTrue(hql.contains("JOIN user.registeredEvents event")); assertTrue(hql.contains("user.id = :userId"));
        assertFalse(hql.contains("DISTINCT")); assertFalse(hql.contains("status"));
        verify(countQuery).setParameter("userId", 31L);
    }

    @Test void globalRegistrationsRetainAllUserAndEventLifecycleHistory() {
        repository.countRegistrations(null); assertFalse(hql.contains("WHERE"));
        verify(countQuery, never()).setParameter(anyString(), any(Object.class));
    }

    @Test void floorFilterCountsOnlyActiveRooms() {
        repository.roomCounts(2); assertTrue(hql.contains("room.active = TRUE"));
        assertTrue(hql.contains("room.floor = :floor")); assertTrue(hql.contains("GROUP BY room.status"));
        verify(groupQuery).setParameter("floor", 2);
    }

    @Test void defaultRoomsDoNotRestrictFloorOrStatus() {
        repository.roomCounts(null); assertFalse(hql.contains("room.floor")); assertFalse(hql.contains("room.status ="));
    }

    @Test void taskDepartmentScopeIncludesInactiveAndTerminalHistory() {
        repository.taskCounts(3L, null); assertTrue(hql.contains("task.department.id = :departmentId"));
        assertFalse(hql.contains("assignedEmployee")); assertFalse(hql.contains("active"));
        assertFalse(hql.contains("task.status IN")); verify(groupQuery).setParameter("departmentId", 3L);
    }

    @Test void staffTaskScopeIsOwnAssigneeNotDepartmentWorkload() {
        repository.taskCounts(null, 12L); assertTrue(hql.contains("task.assignedEmployee.id = :employeeId"));
        assertFalse(hql.contains("task.department")); verify(groupQuery).setParameter("employeeId", 12L);
    }

    @Test void globalTasksHaveNoIdentityDepartmentOrLifecycleRestriction() {
        repository.taskCounts(null, null); assertFalse(hql.contains("department"));
        assertFalse(hql.contains("assignedEmployee")); assertFalse(hql.contains("task.status IN"));
    }

    @Test void overdueUsesStrictTimeNonNullDueAndOnlyNonTerminalStatuses() {
        repository.overdueTaskCount(3L, null, NOW);
        assertTrue(hql.contains("task.dueAt IS NOT NULL AND task.dueAt < :now"));
        assertFalse(hql.contains("<= :now")); verify(countQuery).setParameter("now", NOW);
        verify(countQuery).setParameter("statuses", NON_TERMINAL); verify(countQuery).setParameter("departmentId", 3L);
    }

    @Test void staffOverdueUsesAssigneeIdentity() {
        repository.overdueTaskCount(null, 12L, NOW); assertTrue(hql.contains("task.assignedEmployee.id = :employeeId"));
        verify(countQuery).setParameter("employeeId", 12L);
    }

    @Test void alertsUseRecipientAndHaveNoActiveEmployeeFilter() {
        repository.alertCounts(12L); assertTrue(hql.contains("alert.employee.id = :employeeId"));
        assertFalse(hql.contains("active")); verify(groupQuery).setParameter("employeeId", 12L);
    }

    @Test void globalAlertsIncludeRetainedInactiveRecipientHistory() {
        repository.alertCounts(null); assertFalse(hql.contains("WHERE"));
    }

    @Test void inventoryCountsItemsNotQuantitiesAndFiltersExactDepartment() {
        repository.activeInventoryItemCount(3L); assertTrue(hql.contains("COUNT(item)"));
        assertTrue(hql.contains("item.active = TRUE")); assertTrue(hql.contains("item.department.id = :departmentId"));
        assertFalse(hql.contains("SUM")); verify(countQuery).setParameter("departmentId", 3L);
    }

    @Test void lowStockThresholdIsInclusiveAndIgnoresDeactivatedItems() {
        repository.lowStockItemCount(null); assertTrue(hql.contains("item.active = TRUE"));
        assertTrue(hql.contains("item.quantity <= item.reorderThreshold")); assertFalse(hql.contains("department"));
    }

    @Test void eventCountsIncludeAllStatusesWithoutInventoryDepartmentFilter() {
        repository.eventCounts(); assertTrue(hql.contains("GROUP BY event.status")); assertFalse(hql.contains("WHERE"));
    }

    @Test void eventTaskCountIncludesCancelledHistoryButExcludesUnlinkedTasks() {
        repository.eventTaskCount(); assertTrue(hql.contains("task.event IS NOT NULL")); assertFalse(hql.contains("status"));
    }

    @Test void completedPreparationIsEventLinkedCompletedTasksOnly() {
        repository.completedEventTaskCount(); assertTrue(hql.contains("task.event IS NOT NULL AND task.status = :status"));
        verify(countQuery).setParameter("status", Task.Status.COMPLETED);
    }

    @Test void dashboardDepartmentCountIsActiveOnly() {
        repository.activeDepartmentCount(); assertTrue(hql.contains("department.active = TRUE"));
    }

    @Test void staffingCountsIncludeActiveEmployeesWithoutUserLogins() {
        repository.activeEmployeesByDepartment(List.of(3L, 7L));
        assertFalse(hql.contains("employee.user")); assertTrue(hql.contains("GROUP BY employee.department.id"));
        verify(groupQuery).setParameter("ids", List.of(3L, 7L)); verify(groupQuery).setParameter("status", Employee.Status.ACTIVE);
    }

    @Test void workloadUsesTaskDepartmentAndCanonicalNonTerminalSet() {
        repository.nonTerminalTasksByDepartment(List.of(3L)); assertTrue(hql.contains("GROUP BY task.department.id"));
        assertFalse(hql.contains("assignedEmployee")); verify(groupQuery).setParameter("statuses", NON_TERMINAL);
    }

    @Test void departmentOverdueUsesSameStrictBoundaryAndScopedIds() {
        repository.overdueTasksByDepartment(List.of(3L), NOW); assertTrue(hql.contains("task.dueAt < :now"));
        verify(groupQuery).setParameter("ids", List.of(3L)); verify(groupQuery).setParameter("now", NOW);
        verify(groupQuery).setParameter("statuses", NON_TERMINAL);
    }
}

package com.rockey.hospitality.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rockey.hospitality.dto.CommonDtos.PagedResponse;
import com.rockey.hospitality.entity.Event;
import com.rockey.hospitality.entity.Room;
import com.rockey.hospitality.entity.Task;
import com.rockey.hospitality.entity.User;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * STUDY NOTE: DTO means Data Transfer Object: a plain Java type that carries request, response or internal
 * input data.
 * This container groups role-specific dashboard and aggregate responses, with only registration counts for
 * USER.
 * Controllers and services use these types to separate the API contract from JPA entities and avoid
 * exposing database objects directly.
 * Static nested types need no container instance; validation/JSON annotations describe data, not access
 * permissions.
 */
public final class AnalyticsDtos {

    // @JsonInclude controls which empty/null properties are omitted from JSON; it does not grant access.

    // Namespace only; callers construct the nested types instead.
    private AnalyticsDtos() { }

    /**
     * Safe role-specific dashboard DTO populated by AnalyticsService.
     * Only the permitted role section is present, preventing operational data from being returned to USER.
     */
    public static class DashboardResponse {
        /**
         * USER/STAFF/ADMIN security role used by backend authorization.
         */
        private final User.Role role;
        /**
         * Offset timestamp describing when these backend aggregates were evaluated.
         */
        private final OffsetDateTime asOf;
        /**
         * USER-only count section; service selects this instead of operational sections.
         */
        private final UserDashboard user;
        /**
         * STAFF-only Employee/Department-scoped count section.
         */
        private final StaffDashboard staff;
        /**
         * ADMIN-only global operational count section.
         */
        private final AdminDashboard admin;

        /**
         * Packages the listed response fields supplied by the service without serializing a persistence entity.
         */
        public DashboardResponse(User.Role role, OffsetDateTime asOf, UserDashboard user,
                StaffDashboard staff, AdminDashboard admin) {
            this.role = role;
            this.asOf = asOf;
            this.user = user;
            this.staff = staff;
            this.admin = admin;
        }
        public User.Role getRole() { return role; }
        public OffsetDateTime getAsOf() { return asOf; }
        // Omits null properties from JSON; it does not grant permission or filter data itself.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public UserDashboard getUser() { return user; }
        // Omits null properties from JSON; it does not grant permission or filter data itself.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public StaffDashboard getStaff() { return staff; }
        // Omits null properties from JSON; it does not grant permission or filter data itself.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public AdminDashboard getAdmin() { return admin; }

        /**
         * Contains only the current USER's retained registration count; it has no hotel operational metrics.
         */
        public static class UserDashboard {
            /**
             * Count of this User's retained Event memberships, including membership in historical Events.
             */
            private final long registrationCount;
            /**
             * Packages the listed response fields supplied by the service without serializing a persistence entity.
             */
            public UserDashboard(long registrationCount) { this.registrationCount = registrationCount; }
            public long getRegistrationCount() { return registrationCount; }
        }

        /**
         * Contains the eligible STAFF caller's own Task and Alert counts and its Department's Inventory counts.
         */
        public static class StaffDashboard {
            /**
             * Eligible current caller's linked Employee identity, not a selectable cross-employee scope.
             */
            private final Long employeeId;
            /**
             * Eligible caller's work Department used for Inventory counts.
             */
            private final Long departmentId;
            /**
             * This STAFF Employee's OPEN, ASSIGNED, and IN_PROGRESS Task count.
             */
            private final long nonTerminalAssignedTaskCount;
            /**
             * This STAFF Employee's non-terminal Tasks with non-null dueAt strictly before asOf.
             */
            private final long overdueAssignedTaskCount;
            /**
             * This STAFF Employee's alerts whose status is UNREAD.
             */
            private final long unreadAlertCount;
            /**
             * UNREAD plus READ Alert count in this DTO's permitted recipient or global scope.
             */
            private final long unresolvedAlertCount;
            /**
             * Number of active InventoryItem rows in this DTO's permitted scope, not the sum of stock units.
             */
            private final long activeInventoryItemCount;
            /**
             * Active InventoryItem rows in scope with quantity at or below reorderThreshold.
             */
            private final long lowStockItemCount;

            /**
             * Packages the listed response fields supplied by the service without serializing a persistence entity.
             */
            public StaffDashboard(Long employeeId, Long departmentId, long nonTerminalAssignedTaskCount,
                    long overdueAssignedTaskCount, long unreadAlertCount, long unresolvedAlertCount,
                    long activeInventoryItemCount, long lowStockItemCount) {
                this.employeeId = employeeId;
                this.departmentId = departmentId;
                this.nonTerminalAssignedTaskCount = nonTerminalAssignedTaskCount;
                this.overdueAssignedTaskCount = overdueAssignedTaskCount;
                this.unreadAlertCount = unreadAlertCount;
                this.unresolvedAlertCount = unresolvedAlertCount;
                this.activeInventoryItemCount = activeInventoryItemCount;
                this.lowStockItemCount = lowStockItemCount;
            }
            public Long getEmployeeId() { return employeeId; }
            public Long getDepartmentId() { return departmentId; }
            public long getNonTerminalAssignedTaskCount() { return nonTerminalAssignedTaskCount; }
            public long getOverdueAssignedTaskCount() { return overdueAssignedTaskCount; }
            public long getUnreadAlertCount() { return unreadAlertCount; }
            public long getUnresolvedAlertCount() { return unresolvedAlertCount; }
            public long getActiveInventoryItemCount() { return activeInventoryItemCount; }
            public long getLowStockItemCount() { return lowStockItemCount; }
        }

        /**
         * Contains approved global operational counts for ADMIN, built by backend aggregates rather than frontend list calculations.
         */
        public static class AdminDashboard {
            /**
             * Count of active Rooms in scope, independent of turnover status.
             */
            private final long activeRoomCount;
            /**
             * Count of active Rooms with status READY.
             */
            private final long readyRoomCount;
            /**
             * OPEN, ASSIGNED, and IN_PROGRESS Tasks in the permitted global or Department scope.
             */
            private final long nonTerminalTaskCount;
            /**
             * Non-terminal Tasks in scope with non-null dueAt strictly before asOf.
             */
            private final long overdueTaskCount;
            /**
             * Tasks with status COMPLETED in the dashboard, or completed preparation Tasks in an Event response.
             */
            private final long completedTaskCount;
            /**
             * Departments with active=true.
             */
            private final long activeDepartmentCount;
            /**
             * UNREAD plus READ Alert count in this DTO's permitted recipient or global scope.
             */
            private final long unresolvedAlertCount;
            /**
             * Number of active InventoryItem rows in this DTO's permitted scope, not the sum of stock units.
             */
            private final long activeInventoryItemCount;
            /**
             * Active InventoryItem rows in scope with quantity at or below reorderThreshold.
             */
            private final long lowStockItemCount;
            /**
             * Events in DRAFT, OPEN, CLOSED, or IN_PROGRESS; COMPLETED/CANCELLED are excluded.
             */
            private final long nonTerminalEventCount;
            /**
             * Count of all retained User→Event memberships, not distinct Event or User count.
             */
            private final long registrationCount;

            /**
             * Packages the listed response fields supplied by the service without serializing a persistence entity.
             */
            public AdminDashboard(long activeRoomCount, long readyRoomCount, long nonTerminalTaskCount,
                    long overdueTaskCount, long completedTaskCount, long activeDepartmentCount,
                    long unresolvedAlertCount, long activeInventoryItemCount, long lowStockItemCount,
                    long nonTerminalEventCount, long registrationCount) {
                this.activeRoomCount = activeRoomCount;
                this.readyRoomCount = readyRoomCount;
                this.nonTerminalTaskCount = nonTerminalTaskCount;
                this.overdueTaskCount = overdueTaskCount;
                this.completedTaskCount = completedTaskCount;
                this.activeDepartmentCount = activeDepartmentCount;
                this.unresolvedAlertCount = unresolvedAlertCount;
                this.activeInventoryItemCount = activeInventoryItemCount;
                this.lowStockItemCount = lowStockItemCount;
                this.nonTerminalEventCount = nonTerminalEventCount;
                this.registrationCount = registrationCount;
            }
            public long getActiveRoomCount() { return activeRoomCount; }
            public long getReadyRoomCount() { return readyRoomCount; }
            public long getNonTerminalTaskCount() { return nonTerminalTaskCount; }
            public long getOverdueTaskCount() { return overdueTaskCount; }
            public long getCompletedTaskCount() { return completedTaskCount; }
            public long getActiveDepartmentCount() { return activeDepartmentCount; }
            public long getUnresolvedAlertCount() { return unresolvedAlertCount; }
            public long getActiveInventoryItemCount() { return activeInventoryItemCount; }
            public long getLowStockItemCount() { return lowStockItemCount; }
            public long getNonTerminalEventCount() { return nonTerminalEventCount; }
            public long getRegistrationCount() { return registrationCount; }
        }
    }

    /**
     * Read-only analytics response containing an as-of timestamp and a page of Department workload summaries.
     */
    public static class DepartmentAnalyticsResponse {
        /**
         * Offset timestamp describing when these backend aggregates were evaluated.
         */
        private final OffsetDateTime asOf;
        /**
         * Paged Department workload DTOs including lifecycle and zero-filled counts.
         */
        private final PagedResponse<DepartmentWorkloadSummary> departments;

        /**
         * Packages the listed response fields supplied by the service without serializing a persistence entity.
         */
        public DepartmentAnalyticsResponse(OffsetDateTime asOf, PagedResponse<DepartmentWorkloadSummary> departments) {
            this.asOf = asOf;
            this.departments = departments;
        }
        public OffsetDateTime getAsOf() { return asOf; }
        public PagedResponse<DepartmentWorkloadSummary> getDepartments() { return departments; }
    }

    /**
     * Read-only Department identity and lifecycle with independently queried active Employee and non-terminal/overdue Task counts.
     */
    public static class DepartmentWorkloadSummary {
        /**
         * Department identifier used for an explicit relationship or optional query scope.
         */
        private final Long departmentId;
        /**
         * Display name used by this resource's request or response, not an authorization role.
         */
        private final String name;
        /**
         * Soft-lifecycle flag; inactive rows retain their identity and history.
         */
        private final boolean active;
        /**
         * Employees with status ACTIVE in this Department, including its retained Department history.
         */
        private final long activeEmployeeCount;
        /**
         * OPEN, ASSIGNED, and IN_PROGRESS Tasks in the permitted global or Department scope.
         */
        private final long nonTerminalTaskCount;
        /**
         * Non-terminal Tasks in scope with non-null dueAt strictly before asOf.
         */
        private final long overdueTaskCount;

        /**
         * Packages the listed response fields supplied by the service without serializing a persistence entity.
         */
        public DepartmentWorkloadSummary(Long departmentId, String name, boolean active,
                long activeEmployeeCount, long nonTerminalTaskCount, long overdueTaskCount) {
            this.departmentId = departmentId;
            this.name = name;
            this.active = active;
            this.activeEmployeeCount = activeEmployeeCount;
            this.nonTerminalTaskCount = nonTerminalTaskCount;
            this.overdueTaskCount = overdueTaskCount;
        }
        public Long getDepartmentId() { return departmentId; }
        public String getName() { return name; }
        public boolean getActive() { return active; }
        public long getActiveEmployeeCount() { return activeEmployeeCount; }
        public long getNonTerminalTaskCount() { return nonTerminalTaskCount; }
        public long getOverdueTaskCount() { return overdueTaskCount; }
    }

    /**
     * Read-only combined Inventory and Event counts.
     * Department filtering affects Inventory only; Event and registration aggregates remain global.
     */
    public static class OperationsAnalyticsResponse {
        /**
         * Offset timestamp describing when these backend aggregates were evaluated.
         */
        private final OffsetDateTime asOf;
        /**
         * Inventory aggregates limited by the optional Department filter.
         */
        private final InventoryCounts inventory;
        /**
         * Global Event aggregates; the Inventory Department filter does not apply here.
         */
        private final EventCounts events;

        /**
         * Packages the listed response fields supplied by the service without serializing a persistence entity.
         */
        public OperationsAnalyticsResponse(OffsetDateTime asOf, InventoryCounts inventory, EventCounts events) {
            this.asOf = asOf;
            this.inventory = inventory;
            this.events = events;
        }
        public OffsetDateTime getAsOf() { return asOf; }
        public InventoryCounts getInventory() { return inventory; }
        public EventCounts getEvents() { return events; }

        /**
         * Read-only active and low-stock Inventory counts in the optional Department scope.
         */
        public static class InventoryCounts {
            /**
             * Optional Inventory-only Department filter; null means all Departments.
             */
            private final Long departmentId;
            /**
             * Number of active InventoryItem rows in this DTO's permitted scope, not the sum of stock units.
             */
            private final long activeInventoryItemCount;
            /**
             * Active InventoryItem rows in scope with quantity at or below reorderThreshold.
             */
            private final long lowStockItemCount;

            /**
             * Packages the listed response fields supplied by the service without serializing a persistence entity.
             */
            public InventoryCounts(Long departmentId, long activeInventoryItemCount, long lowStockItemCount) {
                this.departmentId = departmentId;
                this.activeInventoryItemCount = activeInventoryItemCount;
                this.lowStockItemCount = lowStockItemCount;
            }
            public Long getDepartmentId() { return departmentId; }
            public long getActiveInventoryItemCount() { return activeInventoryItemCount; }
            public long getLowStockItemCount() { return lowStockItemCount; }
        }

        /**
         * Read-only global Event lifecycle, retained registration, and preparation-Task counts.
         */
        public static class EventCounts {
            /**
             * All retained Events, including completed and cancelled history.
             */
            private final long eventCount;
            /**
             * Retained Event counts for every EventStatus, including terminal history and zero groups.
             */
            private final Map<Event.Status, Long> eventCountsByStatus;
            /**
             * Count of all retained User→Event memberships, including historical Events.
             */
            private final long registrationCount;
            /**
             * All retained Tasks with a non-null Event relationship.
             */
            private final long eventTaskCount;
            /**
             * Event-linked Tasks with status COMPLETED.
             */
            private final long completedEventTaskCount;

            /**
             * Copies the status counts into an unmodifiable EnumMap so later changes to the source map cannot alter this response.
             */
            public EventCounts(long eventCount, Map<Event.Status, Long> eventCountsByStatus,
                    long registrationCount, long eventTaskCount, long completedEventTaskCount) {
                this.eventCount = eventCount;
                this.eventCountsByStatus = Collections.unmodifiableMap(new EnumMap<>(eventCountsByStatus));
                this.registrationCount = registrationCount;
                this.eventTaskCount = eventTaskCount;
                this.completedEventTaskCount = completedEventTaskCount;
            }
            public long getEventCount() { return eventCount; }
            public Map<Event.Status, Long> getEventCountsByStatus() { return eventCountsByStatus; }
            public long getRegistrationCount() { return registrationCount; }
            public long getEventTaskCount() { return eventTaskCount; }
            public long getCompletedEventTaskCount() { return completedEventTaskCount; }
        }
    }

    /**
     * Read-only active Room counts with the optional floor scope and a zero-filled status map.
     */
    public static class RoomAnalyticsResponse {
        /**
         * Offset timestamp describing when these backend aggregates were evaluated.
         */
        private final OffsetDateTime asOf;
        /**
         * Room floor or optional floor filter; service validation constrains supplied values.
         */
        private final Integer floor;
        /**
         * Count of active Rooms in scope, independent of turnover status.
         */
        private final long activeRoomCount;
        /**
         * Active Room counts for every RoomStatus, with missing database groups represented as zero.
         */
        private final Map<Room.Status, Long> roomCountsByStatus;

        /**
         * Copies the status counts into an unmodifiable EnumMap so later changes to the source map cannot alter this response.
         */
        public RoomAnalyticsResponse(OffsetDateTime asOf, Integer floor, long activeRoomCount,
                                     Map<Room.Status, Long> roomCountsByStatus) {
            this.asOf = asOf;
            this.floor = floor;
            this.activeRoomCount = activeRoomCount;
            this.roomCountsByStatus = Collections.unmodifiableMap(new EnumMap<>(roomCountsByStatus));
        }
        public OffsetDateTime getAsOf() { return asOf; }
        public Integer getFloor() { return floor; }
        public long getActiveRoomCount() { return activeRoomCount; }
        public Map<Room.Status, Long> getRoomCountsByStatus() { return roomCountsByStatus; }
    }

    /**
     * Read-only retained Task counts with optional Department scope, including status totals and strict overdue count.
     */
    public static class TaskAnalyticsResponse {
        /**
         * Offset timestamp describing when these backend aggregates were evaluated.
         */
        private final OffsetDateTime asOf;
        /**
         * Department identifier used for an explicit relationship or optional query scope.
         */
        private final Long departmentId;
        /**
         * All retained Tasks in the optional Department scope, including terminal history.
         */
        private final long totalTaskCount;
        /**
         * Retained Task counts for every TaskStatus, including terminal history and zero groups.
         */
        private final Map<Task.Status, Long> taskCountsByStatus;
        /**
         * Non-terminal Tasks in scope with non-null dueAt strictly before asOf.
         */
        private final long overdueTaskCount;

        /**
         * Copies the status counts into an unmodifiable EnumMap so later changes to the source map cannot alter this response.
         */
        public TaskAnalyticsResponse(OffsetDateTime asOf, Long departmentId, long totalTaskCount,
                                     Map<Task.Status, Long> taskCountsByStatus, long overdueTaskCount) {
            this.asOf = asOf;
            this.departmentId = departmentId;
            this.totalTaskCount = totalTaskCount;
            this.taskCountsByStatus = Collections.unmodifiableMap(new EnumMap<>(taskCountsByStatus));
            this.overdueTaskCount = overdueTaskCount;
        }
        public OffsetDateTime getAsOf() { return asOf; }
        public Long getDepartmentId() { return departmentId; }
        public long getTotalTaskCount() { return totalTaskCount; }
        public Map<Task.Status, Long> getTaskCountsByStatus() { return taskCountsByStatus; }
        public long getOverdueTaskCount() { return overdueTaskCount; }
    }
}

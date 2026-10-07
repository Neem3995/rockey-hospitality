package com.rockey.hospitality.dto.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rockey.hospitality.entity.Role;
import java.time.OffsetDateTime;

/**
 * Safe role-specific dashboard DTO populated by AnalyticsService.
 * Only the permitted role section is present, preventing operational data from being returned to USER.
 */
public class DashboardResponse {
    /**
     * USER/STAFF/ADMIN security role used by backend authorization.
     */
    private final Role role;
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
    public DashboardResponse(Role role, OffsetDateTime asOf, UserDashboard user,
            StaffDashboard staff, AdminDashboard admin) {
        this.role = role;
        this.asOf = asOf;
        this.user = user;
        this.staff = staff;
        this.admin = admin;
    }
    public Role getRole() { return role; }
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

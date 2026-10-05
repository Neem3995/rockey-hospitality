package com.rockey.hospitality.dto.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rockey.hospitality.entity.Role;
import java.time.OffsetDateTime;

public class DashboardResponse {
    private final Role role;
    private final OffsetDateTime asOf;
    private final UserDashboard user;
    private final StaffDashboard staff;
    private final AdminDashboard admin;

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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public UserDashboard getUser() { return user; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public StaffDashboard getStaff() { return staff; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public AdminDashboard getAdmin() { return admin; }

    public static class UserDashboard {
        private final long registrationCount;
        public UserDashboard(long registrationCount) { this.registrationCount = registrationCount; }
        public long getRegistrationCount() { return registrationCount; }
    }

    public static class StaffDashboard {
        private final Long employeeId;
        private final Long departmentId;
        private final long nonTerminalAssignedTaskCount;
        private final long overdueAssignedTaskCount;
        private final long unreadAlertCount;
        private final long unresolvedAlertCount;
        private final long activeInventoryItemCount;
        private final long lowStockItemCount;

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

    public static class AdminDashboard {
        private final long activeRoomCount;
        private final long readyRoomCount;
        private final long nonTerminalTaskCount;
        private final long overdueTaskCount;
        private final long completedTaskCount;
        private final long activeDepartmentCount;
        private final long unresolvedAlertCount;
        private final long activeInventoryItemCount;
        private final long lowStockItemCount;
        private final long nonTerminalEventCount;
        private final long registrationCount;

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

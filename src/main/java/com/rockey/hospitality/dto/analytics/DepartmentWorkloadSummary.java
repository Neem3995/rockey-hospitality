package com.rockey.hospitality.dto.analytics;

/**
 * Read-only Department identity and lifecycle with independently queried active Employee and non-terminal/overdue Task counts.
 */
public class DepartmentWorkloadSummary {
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

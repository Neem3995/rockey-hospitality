package com.rockey.hospitality.dto.analytics;

public class DepartmentWorkloadSummary {
    private final Long departmentId;
    private final String name;
    private final boolean active;
    private final long activeEmployeeCount;
    private final long nonTerminalTaskCount;
    private final long overdueTaskCount;

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

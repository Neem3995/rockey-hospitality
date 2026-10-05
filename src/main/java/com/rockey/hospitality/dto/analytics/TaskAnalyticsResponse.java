package com.rockey.hospitality.dto.analytics;

import com.rockey.hospitality.entity.TaskStatus;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class TaskAnalyticsResponse {
    private final OffsetDateTime asOf;
    private final Long departmentId;
    private final long totalTaskCount;
    private final Map<TaskStatus, Long> taskCountsByStatus;
    private final long overdueTaskCount;

    public TaskAnalyticsResponse(OffsetDateTime asOf, Long departmentId, long totalTaskCount,
                                 Map<TaskStatus, Long> taskCountsByStatus, long overdueTaskCount) {
        this.asOf = asOf;
        this.departmentId = departmentId;
        this.totalTaskCount = totalTaskCount;
        this.taskCountsByStatus = Collections.unmodifiableMap(new EnumMap<>(taskCountsByStatus));
        this.overdueTaskCount = overdueTaskCount;
    }
    public OffsetDateTime getAsOf() { return asOf; }
    public Long getDepartmentId() { return departmentId; }
    public long getTotalTaskCount() { return totalTaskCount; }
    public Map<TaskStatus, Long> getTaskCountsByStatus() { return taskCountsByStatus; }
    public long getOverdueTaskCount() { return overdueTaskCount; }
}

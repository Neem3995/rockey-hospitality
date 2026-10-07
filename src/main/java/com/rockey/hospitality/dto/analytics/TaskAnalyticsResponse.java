package com.rockey.hospitality.dto.analytics;

import com.rockey.hospitality.entity.TaskStatus;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Read-only retained Task counts with optional Department scope, including status totals and strict overdue count.
 */
public class TaskAnalyticsResponse {
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
    private final Map<TaskStatus, Long> taskCountsByStatus;
    /**
     * Non-terminal Tasks in scope with non-null dueAt strictly before asOf.
     */
    private final long overdueTaskCount;

    /**
     * Copies the status counts into an unmodifiable EnumMap so later changes to the source map cannot alter this response.
     */
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

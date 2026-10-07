package com.rockey.hospitality.dto.analytics;

import com.rockey.hospitality.dto.common.PagedResponse;
import java.time.OffsetDateTime;

/**
 * Read-only analytics response containing an as-of timestamp and a page of Department workload summaries.
 */
public class DepartmentAnalyticsResponse {
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

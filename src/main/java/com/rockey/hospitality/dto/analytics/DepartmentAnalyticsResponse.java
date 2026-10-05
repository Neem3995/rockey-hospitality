package com.rockey.hospitality.dto.analytics;

import com.rockey.hospitality.dto.common.PagedResponse;
import java.time.OffsetDateTime;

public class DepartmentAnalyticsResponse {
    private final OffsetDateTime asOf;
    private final PagedResponse<DepartmentWorkloadSummary> departments;

    public DepartmentAnalyticsResponse(OffsetDateTime asOf, PagedResponse<DepartmentWorkloadSummary> departments) {
        this.asOf = asOf;
        this.departments = departments;
    }
    public OffsetDateTime getAsOf() { return asOf; }
    public PagedResponse<DepartmentWorkloadSummary> getDepartments() { return departments; }
}

package com.rockey.hospitality.entity;

/**
 * Controls whether an employee profile is ACTIVE or INACTIVE.
 * Linked User eligibility is synchronized by EmployeeService.
 */
public enum EmployeeStatus {
    /**
     * Eligible active work profile.
     */
    ACTIVE,
    /**
     * Retained profile disabled for new operational assignments.
     */
    INACTIVE
}

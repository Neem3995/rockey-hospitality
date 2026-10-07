package com.rockey.hospitality.dto.task;

import com.rockey.hospitality.entity.TaskPriority;
import com.rockey.hospitality.entity.TaskStatus;

/** Internal Task filters shared by service and bound repository queries. */
/**
 * Immutable optional Task filters used by the service and parameterized repository search.
 * overdue=true, false, and null retain their distinct contract meanings.
 */
public record TaskSearchCriteria(
        /**
         * Optional Department filter, distinct from a writable Task relationship.
         */
        Long departmentId,
        /**
         * Optional exact-status filter, including terminal history when selected.
         */
        TaskStatus status,
        /**
         * Optional exact-priority filter; null leaves priority unfiltered.
         */
        TaskPriority priority,
        /**
         * Optional assignee filter or reference; service checks enforce eligibility and ownership.
         */
        Long assignedEmployeeId,
        /**
         * Optional Room context identifier or search filter.
         */
        Long roomId,
        /**
         * Optional Event preparation identifier or search filter.
         */
        Long eventId,
        /**
         * Optional due-condition filter: true selects overdue work, false selects its complement, and null leaves due time unfiltered.
         */
        Boolean overdue) { }

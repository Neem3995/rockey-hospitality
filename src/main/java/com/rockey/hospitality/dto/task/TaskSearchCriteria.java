package com.rockey.hospitality.dto.task;

import com.rockey.hospitality.entity.TaskPriority;
import com.rockey.hospitality.entity.TaskStatus;

/** Internal Task filters shared by service and bound repository queries. */
public record TaskSearchCriteria(Long departmentId, TaskStatus status, TaskPriority priority,
        Long assignedEmployeeId, Long roomId, Long eventId, Boolean overdue) { }

package com.rockey.hospitality.dto.alert;

import com.rockey.hospitality.entity.AlertStatus;
import com.rockey.hospitality.entity.AlertType;

/** Internal Alert filters; recipient/identity scope remains service-controlled. */
/**
 * Immutable optional alert filters passed from controller to service; a null value means that filter was not supplied.
 */
public record AlertSearchCriteria(
        /**
         * Optional recipient filter; AlertService restricts STAFF to its own Employee ID.
         */
        Long employeeId,
        /**
         * Optional exact source-type filter.
         */
        AlertType type,
        /**
         * Optional lifecycle filter; omission selects unresolved alerts, while explicit RESOLVED selects history.
         */
        AlertStatus status) { }

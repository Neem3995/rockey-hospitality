package com.rockey.hospitality.dto.alert;

import com.rockey.hospitality.entity.AlertStatus;
import com.rockey.hospitality.entity.AlertType;

/** Internal Alert filters; recipient/identity scope remains service-controlled. */
public record AlertSearchCriteria(Long employeeId, AlertType type, AlertStatus status) { }

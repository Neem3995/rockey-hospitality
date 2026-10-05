package com.rockey.hospitality.dto.room;

import com.rockey.hospitality.entity.RoomStatus;

/** Internal Room filters; authorization remains in the service. */
public record RoomSearchCriteria(RoomStatus status, Integer floor, String roomType, Boolean active) { }

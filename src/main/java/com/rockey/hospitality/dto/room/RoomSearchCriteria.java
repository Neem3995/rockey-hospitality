package com.rockey.hospitality.dto.room;

import com.rockey.hospitality.entity.RoomStatus;

/** Internal Room filters; authorization remains in the service. */
/**
 * Immutable optional Room filters passed between controller and service, not persisted Room state.
 */
public record RoomSearchCriteria(
        /**
         * Optional exact turnover-status filter.
         */
        RoomStatus status,
        /**
         * Optional floor filter checked by RoomService.
         */
        Integer floor,
        /**
         * Optional case-insensitive Room-type filter normalized by RoomService.
         */
        String roomType,
        /**
         * Optional active flag; RoomService restricts STAFF to active Rooms regardless of this filter.
         */
        Boolean active) { }

package com.rockey.hospitality.dto.analytics;

import com.rockey.hospitality.entity.RoomStatus;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Read-only active Room counts with the optional floor scope and a zero-filled status map.
 */
public class RoomAnalyticsResponse {
    /**
     * Offset timestamp describing when these backend aggregates were evaluated.
     */
    private final OffsetDateTime asOf;
    /**
     * Room floor or optional floor filter; service validation constrains supplied values.
     */
    private final Integer floor;
    /**
     * Count of active Rooms in scope, independent of turnover status.
     */
    private final long activeRoomCount;
    /**
     * Active Room counts for every RoomStatus, with missing database groups represented as zero.
     */
    private final Map<RoomStatus, Long> roomCountsByStatus;

    /**
     * Copies the status counts into an unmodifiable EnumMap so later changes to the source map cannot alter this response.
     */
    public RoomAnalyticsResponse(OffsetDateTime asOf, Integer floor, long activeRoomCount,
                                 Map<RoomStatus, Long> roomCountsByStatus) {
        this.asOf = asOf;
        this.floor = floor;
        this.activeRoomCount = activeRoomCount;
        this.roomCountsByStatus = Collections.unmodifiableMap(new EnumMap<>(roomCountsByStatus));
    }
    public OffsetDateTime getAsOf() { return asOf; }
    public Integer getFloor() { return floor; }
    public long getActiveRoomCount() { return activeRoomCount; }
    public Map<RoomStatus, Long> getRoomCountsByStatus() { return roomCountsByStatus; }
}

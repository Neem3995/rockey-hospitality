package com.rockey.hospitality.dto.analytics;

import com.rockey.hospitality.entity.RoomStatus;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class RoomAnalyticsResponse {
    private final OffsetDateTime asOf;
    private final Integer floor;
    private final long activeRoomCount;
    private final Map<RoomStatus, Long> roomCountsByStatus;

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

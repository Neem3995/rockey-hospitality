package com.rockey.hospitality.dto.analytics;

import com.rockey.hospitality.entity.EventStatus;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class OperationsAnalyticsResponse {
    private final OffsetDateTime asOf;
    private final InventoryCounts inventory;
    private final EventCounts events;

    public OperationsAnalyticsResponse(OffsetDateTime asOf, InventoryCounts inventory, EventCounts events) {
        this.asOf = asOf;
        this.inventory = inventory;
        this.events = events;
    }
    public OffsetDateTime getAsOf() { return asOf; }
    public InventoryCounts getInventory() { return inventory; }
    public EventCounts getEvents() { return events; }

    public static class InventoryCounts {
        private final Long departmentId;
        private final long activeInventoryItemCount;
        private final long lowStockItemCount;

        public InventoryCounts(Long departmentId, long activeInventoryItemCount, long lowStockItemCount) {
            this.departmentId = departmentId;
            this.activeInventoryItemCount = activeInventoryItemCount;
            this.lowStockItemCount = lowStockItemCount;
        }
        public Long getDepartmentId() { return departmentId; }
        public long getActiveInventoryItemCount() { return activeInventoryItemCount; }
        public long getLowStockItemCount() { return lowStockItemCount; }
    }

    public static class EventCounts {
        private final long eventCount;
        private final Map<EventStatus, Long> eventCountsByStatus;
        private final long registrationCount;
        private final long eventTaskCount;
        private final long completedEventTaskCount;

        public EventCounts(long eventCount, Map<EventStatus, Long> eventCountsByStatus,
                long registrationCount, long eventTaskCount, long completedEventTaskCount) {
            this.eventCount = eventCount;
            this.eventCountsByStatus = Collections.unmodifiableMap(new EnumMap<>(eventCountsByStatus));
            this.registrationCount = registrationCount;
            this.eventTaskCount = eventTaskCount;
            this.completedEventTaskCount = completedEventTaskCount;
        }
        public long getEventCount() { return eventCount; }
        public Map<EventStatus, Long> getEventCountsByStatus() { return eventCountsByStatus; }
        public long getRegistrationCount() { return registrationCount; }
        public long getEventTaskCount() { return eventTaskCount; }
        public long getCompletedEventTaskCount() { return completedEventTaskCount; }
    }
}

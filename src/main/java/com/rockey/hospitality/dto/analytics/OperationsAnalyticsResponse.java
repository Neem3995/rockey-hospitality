package com.rockey.hospitality.dto.analytics;

import com.rockey.hospitality.entity.EventStatus;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Read-only combined Inventory and Event counts.
 * Department filtering affects Inventory only; Event and registration aggregates remain global.
 */
public class OperationsAnalyticsResponse {
    /**
     * Offset timestamp describing when these backend aggregates were evaluated.
     */
    private final OffsetDateTime asOf;
    /**
     * Inventory aggregates limited by the optional Department filter.
     */
    private final InventoryCounts inventory;
    /**
     * Global Event aggregates; the Inventory Department filter does not apply here.
     */
    private final EventCounts events;

    /**
     * Packages the listed response fields supplied by the service without serializing a persistence entity.
     */
    public OperationsAnalyticsResponse(OffsetDateTime asOf, InventoryCounts inventory, EventCounts events) {
        this.asOf = asOf;
        this.inventory = inventory;
        this.events = events;
    }
    public OffsetDateTime getAsOf() { return asOf; }
    public InventoryCounts getInventory() { return inventory; }
    public EventCounts getEvents() { return events; }

    /**
     * Read-only active and low-stock Inventory counts in the optional Department scope.
     */
    public static class InventoryCounts {
        /**
         * Optional Inventory-only Department filter; null means all Departments.
         */
        private final Long departmentId;
        /**
         * Number of active InventoryItem rows in this DTO's permitted scope, not the sum of stock units.
         */
        private final long activeInventoryItemCount;
        /**
         * Active InventoryItem rows in scope with quantity at or below reorderThreshold.
         */
        private final long lowStockItemCount;

        /**
         * Packages the listed response fields supplied by the service without serializing a persistence entity.
         */
        public InventoryCounts(Long departmentId, long activeInventoryItemCount, long lowStockItemCount) {
            this.departmentId = departmentId;
            this.activeInventoryItemCount = activeInventoryItemCount;
            this.lowStockItemCount = lowStockItemCount;
        }
        public Long getDepartmentId() { return departmentId; }
        public long getActiveInventoryItemCount() { return activeInventoryItemCount; }
        public long getLowStockItemCount() { return lowStockItemCount; }
    }

    /**
     * Read-only global Event lifecycle, retained registration, and preparation-Task counts.
     */
    public static class EventCounts {
        /**
         * All retained Events, including completed and cancelled history.
         */
        private final long eventCount;
        /**
         * Retained Event counts for every EventStatus, including terminal history and zero groups.
         */
        private final Map<EventStatus, Long> eventCountsByStatus;
        /**
         * Count of all retained User→Event memberships, including historical Events.
         */
        private final long registrationCount;
        /**
         * All retained Tasks with a non-null Event relationship.
         */
        private final long eventTaskCount;
        /**
         * Event-linked Tasks with status COMPLETED.
         */
        private final long completedEventTaskCount;

        /**
         * Copies the status counts into an unmodifiable EnumMap so later changes to the source map cannot alter this response.
         */
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

package com.rockey.hospitality.entity;

/**
 * Identifies the source category used for filtering and automation: ROOM, TASK, INVENTORY, or SYSTEM.
 */
public enum AlertType {
    /**
     * Room-readiness source condition.
     */
    ROOM,
    /**
     * Assigned Task overdue or priority source condition.
     */
    TASK,
    /**
     * Stock at or below its threshold.
     */
    INVENTORY,
    /**
     * General system alert category retained in the model.
     */
    SYSTEM }

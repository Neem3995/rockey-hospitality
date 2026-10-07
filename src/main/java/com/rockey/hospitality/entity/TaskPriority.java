package com.rockey.hospitality.entity;

/**
 * Labels work urgency as LOW, MEDIUM, HIGH, or URGENT.
 * MEDIUM is the default; HIGH and URGENT are used by Task alert automation.
 */
public enum TaskPriority {
    /**
     * Lower-priority work.
     */
    LOW,
    /**
     * Default work priority.
     */
    MEDIUM,
    /**
     * High-priority work included in Task alert conditions.
     */
    HIGH,
    /**
     * Urgent work included in Task alert conditions.
     */
    URGENT
}

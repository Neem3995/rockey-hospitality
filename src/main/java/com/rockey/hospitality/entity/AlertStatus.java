package com.rockey.hospitality.entity;

/**
 * Tracks alert lifecycle: UNREAD has not been acknowledged, READ remains unresolved, and RESOLVED preserves finished history.
 */
public enum AlertStatus {
    /**
     * Unacknowledged and unresolved alert.
     */
    UNREAD,
    /**
     * Acknowledged alert still awaiting resolution.
     */
    READ,
    /**
     * Finished alert retained for history.
     */
    RESOLVED }

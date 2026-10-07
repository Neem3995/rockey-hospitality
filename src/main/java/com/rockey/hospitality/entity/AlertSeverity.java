package com.rockey.hospitality.entity;

/**
 * Describes alert urgency: INFO, WARNING, HIGH, or CRITICAL.
 * Current automated alerts use the Alert entity's INFO default.
 */
public enum AlertSeverity {
    /**
     * Informational urgency and the current default.
     */
    INFO,
    /**
     * Warning urgency represented in the model.
     */
    WARNING,
    /**
     * High urgency represented in the model.
     */
    HIGH,
    /**
     * Critical urgency represented in the model.
     */
    CRITICAL }

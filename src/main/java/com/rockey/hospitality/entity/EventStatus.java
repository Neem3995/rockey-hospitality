package com.rockey.hospitality.entity;

/**
 * Defines Event lifecycle from DRAFT through OPEN, CLOSED, and IN_PROGRESS to terminal COMPLETED or CANCELLED.
 * EventService enforces permitted transitions; CLOSED stops registration.
 */
public enum EventStatus {
    /**
     * Event being prepared; self-registration is not open.
     */
    DRAFT,
    /**
     * Upcoming Event accepting eligible registrations within capacity.
     */
    OPEN,
    /**
     * Registration closed before the Event starts.
     */
    CLOSED,
    /**
     * Event underway; allowed completion follows the transition map.
     */
    IN_PROGRESS,
    /**
     * Terminal finished Event retaining registrations and Tasks.
     */
    COMPLETED,
    /**
     * Terminal cancelled Event retaining registrations and Tasks.
     */
    CANCELLED
}

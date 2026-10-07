package com.rockey.hospitality.entity;

/**
 * Describes readiness and turnover states, plus MAINTENANCE and OUT_OF_SERVICE.
 * RoomService's transition map enforces the permitted route back through inspection to READY.
 */
public enum RoomStatus {
    /**
     * Operationally ready Room.
     */
    READY,
    /**
     * Room currently occupied, not a separate booking model.
     */
    OCCUPIED,
    /**
     * Room awaiting cleaning.
     */
    DIRTY,
    /**
     * Room undergoing cleaning.
     */
    CLEANING,
    /**
     * Room awaiting readiness inspection.
     */
    INSPECTION,
    /**
     * Room requiring maintenance before readiness.
     */
    MAINTENANCE,
    /**
     * Room unavailable for operational use; distinct from row deactivation.
     */
    OUT_OF_SERVICE
}

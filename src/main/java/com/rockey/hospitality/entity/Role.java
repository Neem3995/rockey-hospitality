package com.rockey.hospitality.entity;

/**
 * Defines backend security roles: USER for attendee actions, STAFF for authorized operational scope, and ADMIN for oversight.
 * These are not Employee job titles or Department names.
 */
public enum Role {
    /**
     * Attendee account permissions, including own Event registration.
     */
    USER,
    /**
     * Operational permissions restricted by eligible identity and Department scope.
     */
    STAFF,
    /**
     * Operational oversight and management permissions.
     */
    ADMIN
}

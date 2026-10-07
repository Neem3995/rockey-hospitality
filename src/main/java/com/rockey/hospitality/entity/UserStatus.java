package com.rockey.hospitality.entity;

/**
 * Controls whether an account is ACTIVE or INACTIVE.
 * JWT authentication reloads this state, and Employee deactivation also clears linked refresh state.
 */
public enum UserStatus {
    /**
     * Account eligible for authentication.
     */
    ACTIVE,
    /**
     * Retained account that cannot authenticate.
     */
    INACTIVE
}

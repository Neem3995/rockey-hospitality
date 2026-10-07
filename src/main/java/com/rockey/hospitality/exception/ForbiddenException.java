package com.rockey.hospitality.exception;

/**
 * Signals a role, ownership, or eligibility denial; the global handler returns HTTP 403.
 */
public class ForbiddenException extends RuntimeException {

    /**
     * Carries the safe business failure message to the shared HTTP exception handler.
     */
    public ForbiddenException(String message) {
        super(message);
    }
}

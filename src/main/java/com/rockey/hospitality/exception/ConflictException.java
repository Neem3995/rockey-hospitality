package com.rockey.hospitality.exception;

/**
 * Signals a uniqueness, lifecycle, or relationship conflict with existing data; the global handler returns HTTP 409.
 */
public class ConflictException extends RuntimeException {

    /**
     * Carries the safe business failure message to the shared HTTP exception handler.
     */
    public ConflictException(String message) {
        super(message);
    }
}

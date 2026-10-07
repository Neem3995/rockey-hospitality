package com.rockey.hospitality.exception;

/**
 * Signals a missing resource or membership; the global handler returns HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Carries the safe business failure message to the shared HTTP exception handler.
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

package com.rockey.hospitality.exception;

/**
 * Signals invalid input or filter values that cannot be accepted; the global handler returns HTTP 400.
 */
public class BadRequestException extends RuntimeException {

    /**
     * Carries the safe business failure message to the shared HTTP exception handler.
     */
    public BadRequestException(String message) {
        super(message);
    }
}

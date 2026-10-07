package com.rockey.hospitality.exception;

/**
 * Signals that an authentication-attempt window has reached its limit; the global handler returns HTTP 429.
 */
public class RateLimitExceededException extends RuntimeException {

    /**
     * Carries the safe business failure message to the shared HTTP exception handler.
     */
    public RateLimitExceededException(String message) {
        super(message);
    }
}

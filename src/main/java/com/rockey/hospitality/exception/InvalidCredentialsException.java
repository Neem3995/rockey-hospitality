package com.rockey.hospitality.exception;

/**
 * Signals invalid login credentials or an inactive account with a generic message; the global handler returns HTTP 401.
 */
public class InvalidCredentialsException extends RuntimeException {

    /**
     * Carries the safe business failure message to the shared HTTP exception handler.
     */
    public InvalidCredentialsException() {
        super("Invalid email or password.");
    }
}

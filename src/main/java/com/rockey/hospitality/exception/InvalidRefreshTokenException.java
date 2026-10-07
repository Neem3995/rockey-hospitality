package com.rockey.hospitality.exception;

/**
 * Signals absent, invalid, expired, or revoked refresh state without revealing token details; the global handler returns HTTP 401.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    /**
     * Carries the safe business failure message to the shared HTTP exception handler.
     */
    public InvalidRefreshTokenException() {
        super("Refresh session is invalid or expired.");
    }
}

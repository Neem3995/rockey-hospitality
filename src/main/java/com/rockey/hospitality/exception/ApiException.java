package com.rockey.hospitality.exception;

/**
 * STUDY NOTE: An exception interrupts the normal operation to report a failure.
 * ApiException itself groups our nested RuntimeException classes; it is not the thrown exception.
 * Services construct a specific failure, such as ConflictException, when a rule is not satisfied.
 * GlobalExceptionHandler turns it into the matching safe HTTP error; a runtime failure also
 * triggers default transactional rollback. These types do not send JSON or log private values.
 */
public final class ApiException {

    // HTTP study key: 400 bad request; 401 missing/invalid authentication; 403 forbidden for this
    // authenticated caller.
    // 404 resource not found; 409 business/data conflict; 429 too many attempts.
    // Unexpected errors use a safe 500 response; raw internal messages are not sent to clients.

    // Namespace only; callers throw the specific nested failure type.
    private ApiException() { }

    /**
     * Signals invalid input or filter values that cannot be accepted; the global handler returns HTTP 400.
     */
    public static class BadRequestException extends RuntimeException {

        /**
         * Carries the safe business failure message to the shared HTTP exception handler.
         */
        public BadRequestException(String message) {
            super(message);
        }
    }

    /**
     * Signals a uniqueness, lifecycle, or relationship conflict with existing data; the global handler returns HTTP 409.
     */
    public static class ConflictException extends RuntimeException {

        /**
         * Carries the safe business failure message to the shared HTTP exception handler.
         */
        public ConflictException(String message) {
            super(message);
        }
    }

    /**
     * Signals a role, ownership, or eligibility denial; the global handler returns HTTP 403.
     */
    public static class ForbiddenException extends RuntimeException {

        /**
         * Carries the safe business failure message to the shared HTTP exception handler.
         */
        public ForbiddenException(String message) {
            super(message);
        }
    }

    /**
     * Signals invalid login credentials or an inactive account with a generic message; the global handler returns HTTP 401.
     */
    public static class InvalidCredentialsException extends RuntimeException {

        /**
         * Carries the safe business failure message to the shared HTTP exception handler.
         */
        public InvalidCredentialsException() {
            super("Invalid email or password.");
        }
    }

    /**
     * Signals absent, invalid, expired, or revoked refresh state without revealing token details; the global handler returns HTTP 401.
     */
    public static class InvalidRefreshTokenException extends RuntimeException {

        /**
         * Carries the safe business failure message to the shared HTTP exception handler.
         */
        public InvalidRefreshTokenException() {
            super("Refresh session is invalid or expired.");
        }
    }

    /**
     * Signals that an authentication-attempt window has reached its limit; the global handler returns HTTP 429.
     */
    public static class RateLimitExceededException extends RuntimeException {

        /**
         * Carries the safe business failure message to the shared HTTP exception handler.
         */
        public RateLimitExceededException(String message) {
            super(message);
        }
    }

    /**
     * Signals a missing resource or membership; the global handler returns HTTP 404.
     */
    public static class ResourceNotFoundException extends RuntimeException {

        /**
         * Carries the safe business failure message to the shared HTTP exception handler.
         */
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }
}

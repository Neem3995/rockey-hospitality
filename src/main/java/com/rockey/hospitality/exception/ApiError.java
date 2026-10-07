package com.rockey.hospitality.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Safe shared JSON error shape for controller and security failures.
 * Field-validation messages are included when useful, without stack traces or credential data.
 */
// Omits null/empty properties such as absent fieldErrors from serialized errors.
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ApiError {

    /**
     * Server-local time at which this error response was constructed.
     */
    private final LocalDateTime timestamp;
    /**
     * Numeric HTTP error status, not a domain lifecycle enum.
     */
    private final int status;
    /**
     * HTTP status reason label corresponding to the error's numeric status.
     */
    private final String error;
    /**
     * Safe client-facing explanatory text without private authentication or database details.
     */
    private final String message;
    /**
     * Request URI associated with this safe error response.
     */
    private final String path;
    /**
     * Optional first validation message per rejected request field.
     */
    private final Map<String, String> fieldErrors;

    /**
     * Packages safe HTTP error metadata and optional field messages for JSON serialization.
     */
    public ApiError(
            LocalDateTime timestamp,
            int status,
            String error,
            String message,
            String path,
            Map<String, String> fieldErrors
    ) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.fieldErrors = fieldErrors;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}

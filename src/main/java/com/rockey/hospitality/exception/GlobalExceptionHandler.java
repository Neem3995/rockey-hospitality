package com.rockey.hospitality.exception;

import com.rockey.hospitality.dto.CommonDtos.ApiError;
import com.rockey.hospitality.exception.ApiException.BadRequestException;
import com.rockey.hospitality.exception.ApiException.ConflictException;
import com.rockey.hospitality.exception.ApiException.ForbiddenException;
import com.rockey.hospitality.exception.ApiException.InvalidCredentialsException;
import com.rockey.hospitality.exception.ApiException.InvalidRefreshTokenException;
import com.rockey.hospitality.exception.ApiException.RateLimitExceededException;
import com.rockey.hospitality.exception.ApiException.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * STUDY NOTE: A global exception handler converts controller/service failures into consistent HTTP error
 * responses.
 * Here, @RestControllerAdvice applies these mappings across REST controllers; @ExceptionHandler selects a method
 * for listed failure types.
 * CommonDtos.ApiError carries safe status, message, path and optional field errors rather than a stack
 * trace.
 * Unexpected and persistence failures receive generic messages so private implementation details stay out
 * of the response.
 */
// Applies these exception mappings across REST controllers.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // HTTP study key: 400 bad request; 401 missing/invalid authentication; 403 forbidden for this
    // authenticated caller.
    // 404 resource not found; 409 business/data conflict; 429 too many attempts.
    // Unexpected errors use a safe 500 response; raw internal messages are not sent to clients.

    /**
     * Class logger used for safe diagnostics without credential or token values.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Maps a missing resource or registration to a safe 404 response.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, null);
    }

    /**
     * Maps uniqueness or lifecycle conflicts to a safe 409 response.
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(
            ConflictException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request, null);
    }

    /**
     * Maps service input validation failures to a 400 response.
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(
            BadRequestException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request, null);
    }

    /**
     * Maps role, ownership, or eligibility failures to a 403 response.
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(
            ForbiddenException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.FORBIDDEN, exception.getMessage(), request, null);
    }

    /**
     * Maps invalid credentials and refresh sessions to generic 401 responses.
     */
    @ExceptionHandler({
            InvalidCredentialsException.class,
            InvalidRefreshTokenException.class
    })
    public ResponseEntity<ApiError> handleUnauthorized(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage(), request, null);
    }

    /**
     * Maps exhausted authentication-attempt windows to a 429 response.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimit(
            RateLimitExceededException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), request, null);
    }

    /**
     * Collects the first validation message per field and returns a 400 error with fieldErrors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return response(
                HttpStatus.BAD_REQUEST,
                "Request validation failed.",
                request,
                fieldErrors
        );
    }

    /**
     * Returns 400 for unreadable JSON or mismatched request types without exposing parsing internals.
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> handleMalformedRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, "Request could not be read.", request, null);
    }

    /**
     * Returns a generic 409 for persistence integrity conflicts and logs only the request path, not SQL or private values.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        LOGGER.warn("Data integrity conflict on {}", request.getRequestURI());
        return response(
                HttpStatus.CONFLICT,
                "Request conflicts with existing data.",
                request,
                null
        );
    }

    /**
     * Returns a generic 500 and logs only exception type and request path, keeping internal details out of the response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        LOGGER.error(
                "Unexpected {} while handling {}",
                exception.getClass().getSimpleName(),
                request.getRequestURI()
        );
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.",
                request,
                null
        );
    }

    /**
     * Builds the shared error DTO and a ResponseEntity with the same HTTP status.
     */
    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        ApiError body = new ApiError(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.status(status).body(body);
    }
}

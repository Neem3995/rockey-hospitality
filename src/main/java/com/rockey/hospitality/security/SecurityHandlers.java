package com.rockey.hospitality.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockey.hospitality.dto.CommonDtos.ApiError;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * STUDY NOTE: Security failure handlers turn filter-level authentication/authorization failures into safe
 * JSON errors.
 * Here, @Component registers each nested handler as a Spring bean with its original name; constructor injection
 * shares the JSON error writer.
 * Missing/invalid authentication returns 401, while an authenticated caller without permission receives
 * 403.
 * SecurityErrorWriter serializes CommonDtos.ApiError rather than exposing exceptions, credentials or token
 * values.
 */
public final class SecurityHandlers {

    // @Override shows that this method implements a superclass/interface contract rather than inventing a
    // separate hook.

    // Namespace only; Spring creates the nested handler components.
    private SecurityHandlers() { }

    /**
     * Writes a safe JSON 401 response when authentication is missing or invalid, rather than redirecting to an HTML login page.
     */
    @Component("restAuthenticationEntryPoint")
    public static class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

        /**
         * Shared JSON serialization for safe security failures.
         */
        private final SecurityErrorWriter errorWriter;

        /**
         * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
         */
        public RestAuthenticationEntryPoint(SecurityErrorWriter errorWriter) {
            this.errorWriter = errorWriter;
        }

        /**
         * Returns a generic 401 JSON error for absent or invalid authentication.
         */
        @Override
        public void commence(
                HttpServletRequest request,
                HttpServletResponse response,
                AuthenticationException authException
        ) throws IOException, ServletException {
            errorWriter.write(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required or the supplied token is invalid.",
                    request.getRequestURI()
            );
        }
    }

    /**
     * Writes a safe JSON 403 response when an authenticated caller fails a route-level authorization check.
     */
    @Component("restAccessDeniedHandler")
    public static class RestAccessDeniedHandler implements AccessDeniedHandler {

        /**
         * Shared JSON serialization for safe security failures.
         */
        private final SecurityErrorWriter errorWriter;

        /**
         * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
         */
        public RestAccessDeniedHandler(SecurityErrorWriter errorWriter) {
            this.errorWriter = errorWriter;
        }

        /**
         * Returns a generic 403 JSON error for insufficient permission without exposing authentication details.
         */
        @Override
        public void handle(
                HttpServletRequest request,
                HttpServletResponse response,
                AccessDeniedException accessDeniedException
        ) throws IOException, ServletException {
            errorWriter.write(
                    response,
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to perform this operation.",
                    request.getRequestURI()
            );
        }
    }

    /**
     * Serializes security-filter failures using the same safe ApiError shape as controller exceptions.
     */
    @Component("securityErrorWriter")
    public static class SecurityErrorWriter {

        /**
         * Spring's configured JSON mapper, keeping security errors consistent with application serialization.
         */
        private final ObjectMapper objectMapper;

        /**
         * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
         */
        public SecurityErrorWriter(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        /**
         * Sets the requested HTTP status and serializes ApiError as JSON.
         * It omits stack traces, passwords, and token details.
         */
        public void write(
                HttpServletResponse response,
                HttpStatus status,
                String message,
                String path
        ) throws IOException {
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiError body = new ApiError(
                    LocalDateTime.now(),
                    status.value(),
                    status.getReasonPhrase(),
                    message,
                    path,
                    null
            );
            objectMapper.writeValue(response.getOutputStream(), body);
        }
    }
}

package com.rockey.hospitality.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes a safe JSON 403 response when an authenticated caller fails a route-level authorization check.
 */
// Registers this class as a Spring-managed component discovered during startup.
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

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
    // Implements the inherited Java/Spring contract rather than defining a separate callback.
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

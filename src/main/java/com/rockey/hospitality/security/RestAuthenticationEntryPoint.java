package com.rockey.hospitality.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes a safe JSON 401 response when authentication is missing or invalid, rather than redirecting to an HTML login page.
 */
// Registers this class as a Spring-managed component discovered during startup.
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

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
    // Implements the inherited Java/Spring contract rather than defining a separate callback.
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

package com.rockey.hospitality.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockey.hospitality.exception.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Serializes security-filter failures using the same safe ApiError shape as controller exceptions.
 */
// Registers this class as a Spring-managed component discovered during startup.
@Component
public class SecurityErrorWriter {

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

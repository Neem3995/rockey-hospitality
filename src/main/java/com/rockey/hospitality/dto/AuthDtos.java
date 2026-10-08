package com.rockey.hospitality.dto;

import com.rockey.hospitality.entity.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * STUDY NOTE: A DTO is a request/response shape; it is not a stored User entity.
 * Jackson reads login/register JSON into these request objects, and @Valid checks their annotations.
 * RegisterUserRequest has no role field; AuthService creates USER. AuthResponse carries the access
 * JWT and safe profile; AuthController puts the raw refresh token only in the HttpOnly cookie.
 * ApiError is also shared by our error handlers. These classes carry data, not session or password rules.
 */
public final class AuthDtos {
    private AuthDtos() { }
    /** Validated JSON input; no entity or trusted caller identity is accepted from the browser. */
    public static class LoginRequest {
        @NotBlank @Email @Size(max = 120)
        private String email;
        @NotBlank @Size(max = 72)
        private String password;
        /** Jackson constructs the request, then populates properties. */
        public LoginRequest() { }
        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    /** Validated JSON input; no entity or trusted caller identity is accepted from the browser. */
    public static class RegisterUserRequest {
        @NotBlank @Size(min = 2, max = 100)
        private String name;
        @NotBlank @Email @Size(max = 120)
        private String email;
        @NotBlank @Size(min = 8, max = 72)
        private String password;
        /** Jackson constructs the request, then populates properties. */
        public RegisterUserRequest() { }
        public RegisterUserRequest(String name, String email, String password) {
            this.name = name;
            this.email = email;
            this.password = password;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    /** Safe JSON output, without password hashes or refresh session data. */
    public static class CurrentUserResponse {
        private final Long id;
        private final String name;
        private final String email;
        private final User.Role role;
        private final boolean active;
        public CurrentUserResponse(Long id, String name, String email, User.Role role, boolean active) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.role = role;
            this.active = active;
        }
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public User.Role getRole() { return role; }
        public boolean getActive() { return active; }
    }

    /** Safe JSON output, without password hashes or refresh session data. */
    public static class AuthResponse {
        private final String accessToken;
        private final String tokenType;
        private final Instant accessExpiresAt;
        private final Instant refreshExpiresAt;
        private final CurrentUserResponse user;
        public AuthResponse(String accessToken, String tokenType, Instant accessExpiresAt, Instant refreshExpiresAt, CurrentUserResponse user) {
            this.accessToken = accessToken;
            this.tokenType = tokenType;
            this.accessExpiresAt = accessExpiresAt;
            this.refreshExpiresAt = refreshExpiresAt;
            this.user = user;
        }
        public String getAccessToken() { return accessToken; }
        public String getTokenType() { return tokenType; }
        public Instant getAccessExpiresAt() { return accessExpiresAt; }
        public Instant getRefreshExpiresAt() { return refreshExpiresAt; }
        public CurrentUserResponse getUser() { return user; }
    }

    /** Safe JSON output, without password hashes or refresh session data. */
    public static class ApiError {
        private final LocalDateTime timestamp;
        private final int status;
        private final String error;
        private final String message;
        private final String path;
        private final Map<String, String> fieldErrors;
        public ApiError(LocalDateTime timestamp, int status, String error, String message, String path, Map<String, String> fieldErrors) {
            this.timestamp = timestamp;
            this.status = status;
            this.error = error;
            this.message = message;
            this.path = path;
            this.fieldErrors = fieldErrors;
        }
        public LocalDateTime getTimestamp() { return timestamp; }
        public int getStatus() { return status; }
        public String getError() { return error; }
        public String getMessage() { return message; }
        public String getPath() { return path; }
        public Map<String, String> getFieldErrors() { return fieldErrors; }
    }
}

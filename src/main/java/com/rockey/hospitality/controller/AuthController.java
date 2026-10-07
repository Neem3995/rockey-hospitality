package com.rockey.hospitality.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.rockey.hospitality.exception.ApiError;

import com.rockey.hospitality.configuration.SecurityProperties;
import com.rockey.hospitality.dto.auth.AuthResponse;
import com.rockey.hospitality.dto.auth.CurrentUserResponse;
import com.rockey.hospitality.dto.auth.LoginRequest;
import com.rockey.hospitality.dto.auth.RegisterUserRequest;
import com.rockey.hospitality.exception.ForbiddenException;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.AuthService;
import com.rockey.hospitality.service.AuthSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Collections;

/**
 * Binds Auth HTTP requests and delegates business operations to its service layer.
 * Response DTOs and HTTP statuses are kept separate from JPA entities.
 */
// Registers a web controller whose mapped return values are written as response bodies, normally JSON.
@RestController
// Groups this controller's routes under /api/auth.
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Shared refresh-cookie name used for issuing, reading, rotating, and expiring the same browser cookie.
     */
    public static final String REFRESH_COOKIE_NAME = "rockey_refresh";

    /**
     * Injected AuthService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final AuthService authService;
    /**
     * Validated external security configuration shared with token and cookie handling.
     */
    private final SecurityProperties securityProperties;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public AuthController(AuthService authService, SecurityProperties securityProperties) {
        this.authService = authService;
        this.securityProperties = securityProperties;
    }

    /**
     * Delegates public USER registration and writes the separate refresh cookie.
     * ResponseEntity sets 201 and returns only the authentication DTO.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Register guest/event USER", description = "Access: Public, rate limited. Canonical operation: POST /api/auth/register.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "429", description = "Authentication rate limited", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP POST to this /register suffix.
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            // Validates the bound request DTO before the controller delegates to its service.
            @Valid
            // Deserializes the request JSON into this writable DTO.
            @RequestBody RegisterUserRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        AuthSession session = authService.register(
                request.getName(),
                request.getEmail(),
                request.getPassword(),
                servletRequest.getRemoteAddr()
        );
        setRefreshCookie(servletResponse, session);
        return ResponseEntity.status(HttpStatus.CREATED).body(session.getResponse());
    }

    /**
     * Delegates credential validation and writes the rotated refresh cookie, returning the safe access-token/profile response.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Authenticate any active role", description = "Access: Public, rate limited. Canonical operation: POST /api/auth/login.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "429", description = "Authentication rate limited", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP POST to this /login suffix.
    @PostMapping("/login")
    public AuthResponse login(
            // Validates the bound request DTO before the controller delegates to its service.
            @Valid
            // Deserializes the request JSON into this writable DTO.
            @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        AuthSession session = authService.login(
                request.getEmail(),
                request.getPassword(),
                servletRequest.getRemoteAddr()
        );
        setRefreshCookie(servletResponse, session);
        return session.getResponse();
    }

    /**
     * Checks the browser Origin before using the HttpOnly refresh cookie.
     * AuthService rotates the session and this controller replaces the cookie.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Rotate/renew access token", description = "Access: Refresh token. Canonical operation: POST /api/auth/refresh.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "429", description = "Authentication rate limited", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP POST to this /refresh suffix.
    @PostMapping("/refresh")
    public AuthResponse refresh(
            // Binds the optional rockey_refresh cookie to this parameter without exposing it in response JSON.
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        validateRefreshOrigin(servletRequest);
        AuthSession session = authService.refresh(
                refreshToken,
                servletRequest.getRemoteAddr()
        );
        setRefreshCookie(servletResponse, session);
        return session.getResponse();
    }

    /**
     * Rejects a present Origin unless it is a single configured allowed origin.
     * Requests without Origin retain non-browser compatibility.
     */
    private void validateRefreshOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        // Non-browser clients may omit Origin. Browser origins must match the exact CORS allowlist.
        if (origin != null && (!securityProperties.getAllowedOrigins().contains(origin)
                || Collections.list(request.getHeaders(HttpHeaders.ORIGIN)).size() != 1)) {
            throw new ForbiddenException("Refresh origin is not allowed.");
        }
    }

    /**
     * Returns the authenticated principal's own current profile through AuthService.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Own profile", description = "Access: USER, STAFF, ADMIN. Canonical operation: GET /api/auth/me.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP GET to this /me suffix.
    @GetMapping("/me")
    public CurrentUserResponse currentUser(
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        return authService.getCurrentUser(principal.getId());
    }

    /**
     * Revokes the authenticated User's refresh session and expires the same cookie path and name.
     * Returns 204 without token or password data.
     */
    // Documents this operation's purpose and access description in OpenAPI; it does not enforce authorization.
    @Operation(summary = "Revoke current refresh session and expire refresh cookie", description = "Access: USER, STAFF, ADMIN; access token required. Canonical operation: POST /api/auth/logout.")
    // Documents HTTP outcomes: @ApiResponse gives status codes, @Content describes bodies, and @Schema identifies DTO shapes.
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content", content = @Content),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    // Maps HTTP POST to this /logout suffix.
    @PostMapping("/logout")
    // Sets the successful HTTP status to 204 No Content for this bodyless operation.
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            // Uses the identity established by backend authentication, not an ID supplied in JSON.
            @AuthenticationPrincipal RockeyUserPrincipal principal,
            HttpServletResponse servletResponse
    ) {
        authService.logout(principal.getId());
        setRefreshCookie(servletResponse, "", Duration.ZERO);
    }

    /**
     * Sends the session's raw refresh value through the shared cookie writer with the configured lifetime.
     */
    private void setRefreshCookie(
            HttpServletResponse servletResponse,
            AuthSession session
    ) {
        setRefreshCookie(
                servletResponse,
                session.getRawRefreshToken(),
                Duration.ofDays(securityProperties.getRefreshTokenDays())
        );
    }

    /**
     * Writes the HttpOnly cookie with the configured Secure/SameSite flags and /api/auth path.
     * The same writer expires it on logout using zero max-age.
     */
    private void setRefreshCookie(
            HttpServletResponse servletResponse,
            String value,
            Duration maxAge
    ) {
        ResponseCookie cookie = ResponseCookie
                // The browser sends this HttpOnly secret; React receives only the access-token JSON.
                .from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(securityProperties.isRefreshCookieSecure())
                .sameSite(securityProperties.getRefreshCookieSameSite())
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

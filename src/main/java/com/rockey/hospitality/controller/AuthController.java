package com.rockey.hospitality.controller;

import com.rockey.hospitality.configuration.SecurityProperties;
import com.rockey.hospitality.dto.AuthDtos.AuthResponse;
import com.rockey.hospitality.dto.AuthDtos.CurrentUserResponse;
import com.rockey.hospitality.dto.AuthDtos.LoginRequest;
import com.rockey.hospitality.dto.AuthDtos.RegisterUserRequest;
import com.rockey.hospitality.dto.AuthDtos.ApiError;
import com.rockey.hospitality.exception.ApiException.ForbiddenException;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Collections;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * STUDY NOTE: Spring calls these methods when an /api/auth request matches a route.
 * First we read the validated JSON, refresh cookie or authenticated principal, depending on the action.
 * Next AuthService checks accounts and sessions. We return its safe response and set the refresh cookie
 * in an HTTP header, not in JSON for React to read. Logout expires that same cookie.
 * Refresh checks a present Origin against SecurityProperties. Password hashing and token rotation
 * stay in AuthService and its helpers; controller annotations alone do not enforce those rules.
 */
@RestController
// Groups this controller's routes under /api/auth.
@RequestMapping("/api/auth")
public class AuthController {

    // HTTP/annotation study key:
    // @GetMapping handles HTTP GET reads; its path is appended to the controller's base URL.
    // @PostMapping handles HTTP POST creation/actions, such as register, login, refresh and logout.
    // @RequestBody reads request JSON into the declared DTO.
    // @Valid runs the DTO's Bean Validation checks before controller business delegation.
    // @AuthenticationPrincipal supplies the identity established by Spring Security, not a client-chosen User
    // ID.
    // @CookieValue reads the named refresh cookie from the request without exposing it to browser JavaScript.
    // @ResponseStatus declares the HTTP status for a successful controller return.
    // @Operation and @ApiResponses document the operation and its outcomes; they do not authorize or validate
    // requests.
    // @Content/@Schema describe documented bodies/types, not runtime validation or security.
    // An access JWT stays in frontend memory; the longer-lived refresh token stays in its HttpOnly cookie.
    // Present browser Origin is checked on refresh; approved non-browser requests may omit Origin.
    // Logout expires the cookie and revokes refresh state, not already-issued access JWTs.

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
    @Operation(summary = "Register housekeeping USER", description = "Access: Public, rate limited. Canonical operation: POST /api/auth/register.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "429", description = "Authentication rate limited", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid
            @RequestBody RegisterUserRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        AuthService.AuthSession session = authService.register(
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
    @Operation(summary = "Authenticate any active role", description = "Access: Public, rate limited. Canonical operation: POST /api/auth/login.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "429", description = "Authentication rate limited", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/login")
    public AuthResponse login(
            @Valid
            @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        AuthService.AuthSession session = authService.login(
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
    @Operation(summary = "Rotate/renew access token", description = "Access: Refresh token. Canonical operation: POST /api/auth/refresh.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "429", description = "Authentication rate limited", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/refresh")
    public AuthResponse refresh(
            // Binds the optional rockey_refresh cookie to this parameter without exposing it in response JSON.
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        validateRefreshOrigin(servletRequest);
        AuthService.AuthSession session = authService.refresh(
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
    @Operation(summary = "Own profile", description = "Access: USER, MANAGER, ADMIN. Canonical operation: GET /api/auth/me.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/me")
    public CurrentUserResponse currentUser(
            @AuthenticationPrincipal RockeyUserPrincipal principal
    ) {
        return authService.getCurrentUser(principal.getId());
    }

    /**
     * Revokes the authenticated User's refresh session and expires the same cookie path and name.
     * Returns 204 without token or password data.
     */
    @Operation(summary = "Revoke current refresh session and expire refresh cookie", description = "Access: USER, MANAGER, ADMIN; access token required. Canonical operation: POST /api/auth/logout.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content", content = @Content),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/logout")
    // Sets the successful HTTP status to 204 No Content for this bodyless operation.
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
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
            AuthService.AuthSession session
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

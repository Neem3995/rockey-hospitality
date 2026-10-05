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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String REFRESH_COOKIE_NAME = "rockey_refresh";

    private final AuthService authService;
    private final SecurityProperties securityProperties;

    public AuthController(AuthService authService, SecurityProperties securityProperties) {
        this.authService = authService;
        this.securityProperties = securityProperties;
    }

    @Operation(summary = "Register guest/event USER", description = "Access: Public, rate limited. Canonical operation: POST /api/auth/register.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Business or lifecycle conflict", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "429", description = "Authentication rate limited", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterUserRequest request,
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
            @Valid @RequestBody LoginRequest request,
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

    private void validateRefreshOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        // Non-browser clients may omit Origin. Browser origins must match the exact CORS allowlist.
        if (origin != null && (!securityProperties.getAllowedOrigins().contains(origin)
                || Collections.list(request.getHeaders(HttpHeaders.ORIGIN)).size() != 1)) {
            throw new ForbiddenException("Refresh origin is not allowed.");
        }
    }

    @Operation(summary = "Own profile", description = "Access: USER, STAFF, ADMIN. Canonical operation: GET /api/auth/me.")
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

    @Operation(summary = "Revoke current refresh session and expire refresh cookie", description = "Access: USER, STAFF, ADMIN; access token required. Canonical operation: POST /api/auth/logout.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content", content = @Content),
        @ApiResponse(responseCode = "401", description = "Authentication required or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Safe unexpected error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @AuthenticationPrincipal RockeyUserPrincipal principal,
            HttpServletResponse servletResponse
    ) {
        authService.logout(principal.getId());
        setRefreshCookie(servletResponse, "", Duration.ZERO);
    }

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

    private void setRefreshCookie(
            HttpServletResponse servletResponse,
            String value,
            Duration maxAge
    ) {
        ResponseCookie cookie = ResponseCookie
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

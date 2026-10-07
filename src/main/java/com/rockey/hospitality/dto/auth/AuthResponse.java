package com.rockey.hospitality.dto.auth;

import java.time.Instant;

/**
 * Authentication JSON response containing an access JWT, expiry metadata, and safe current-user details.
 * The raw refresh token is delivered separately as an HttpOnly cookie.
 */
public class AuthResponse {

    /**
     * Short-lived access JWT; clients keep it in memory, not browser storage.
     */
    private final String accessToken;
    /**
     * Authorization scheme label used with the returned access JWT.
     */
    private final String tokenType;
    /**
     * Access JWT expiration instant returned as metadata.
     */
    private final Instant accessExpiresAt;
    /**
     * Refresh-session expiration instant returned without the raw refresh token.
     */
    private final Instant refreshExpiresAt;
    /**
     * Safe current-user DTO with no password hash or raw refresh token.
     */
    private final CurrentUserResponse user;

    /**
     * Packages the listed response fields supplied by the service without serializing a persistence entity.
     */
    public AuthResponse(
            String accessToken,
            String tokenType,
            Instant accessExpiresAt,
            Instant refreshExpiresAt,
            CurrentUserResponse user
    ) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.accessExpiresAt = accessExpiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public Instant getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public Instant getRefreshExpiresAt() {
        return refreshExpiresAt;
    }

    public CurrentUserResponse getUser() {
        return user;
    }
}

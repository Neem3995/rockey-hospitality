package com.rockey.hospitality.dto.auth;

import java.time.Instant;

public class AuthResponse {

    private final String accessToken;
    private final String tokenType;
    private final Instant accessExpiresAt;
    private final Instant refreshExpiresAt;
    private final CurrentUserResponse user;

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

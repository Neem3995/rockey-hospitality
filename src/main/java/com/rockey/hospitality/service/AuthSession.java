package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.auth.AuthResponse;

/**
 * Internal service result carrying the safe JSON response separately from the raw refresh cookie value.
 */
public class AuthSession {

    /**
     * Safe access-token and profile JSON response; raw refresh state is carried separately.
     */
    private final AuthResponse response;
    /**
     * Internal raw cookie value; the controller sends it only as an HttpOnly cookie, not response JSON.
     */
    private final String rawRefreshToken;

    /**
     * Packages the safe JSON DTO and secret refresh-cookie value separately for the controller.
     */
    public AuthSession(AuthResponse response, String rawRefreshToken) {
        this.response = response;
        this.rawRefreshToken = rawRefreshToken;
    }

    public AuthResponse getResponse() {
        return response;
    }

    public String getRawRefreshToken() {
        return rawRefreshToken;
    }
}

package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.auth.AuthResponse;

public class AuthSession {

    private final AuthResponse response;
    private final String rawRefreshToken;

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

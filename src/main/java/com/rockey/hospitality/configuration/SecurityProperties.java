package com.rockey.hospitality.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds external security settings without embedding private values in source.
 * These settings control token lifetimes, refresh-cookie flags, and the browser-origin allowlist.
 */
// Registers this class as a Spring-managed component discovered during startup.
@Component
// Enables validation of the bound configuration properties.
@Validated
// Binds rockey.security external properties to this configuration object.
@ConfigurationProperties(prefix = "rockey.security")
public class SecurityProperties {

    /**
     * Required private Base64 signing-key configuration; source contains no key value.
     */
    // Requires non-null text containing at least one non-whitespace character.
    @NotBlank
    private String jwtSecret;

    /**
     * Configured access-token lifetime in minutes, defaulting to fifteen.
     */
    // Rejects a zero or negative configured value when properties are bound at startup.
    @Positive
    private long accessTokenMinutes = 15;

    /**
     * Configured refresh-session and cookie lifetime in days, defaulting to seven.
     */
    // Rejects a zero or negative configured value when properties are bound at startup.
    @Positive
    private long refreshTokenDays = 7;

    /**
     * Configurable HTTPS-only cookie flag; production configuration can enable it without changing code.
     */
    private boolean refreshCookieSecure;
    /**
     * Refresh cookie's configured SameSite policy, defaulting to Lax.
     */
    private String refreshCookieSameSite = "Lax";
    /**
     * Explicit browser-origin allowlist shared by CORS and refresh Origin checks.
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getAccessTokenMinutes() {
        return accessTokenMinutes;
    }

    public void setAccessTokenMinutes(long accessTokenMinutes) {
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public long getRefreshTokenDays() {
        return refreshTokenDays;
    }

    public void setRefreshTokenDays(long refreshTokenDays) {
        this.refreshTokenDays = refreshTokenDays;
    }

    public boolean isRefreshCookieSecure() {
        return refreshCookieSecure;
    }

    public void setRefreshCookieSecure(boolean refreshCookieSecure) {
        this.refreshCookieSecure = refreshCookieSecure;
    }

    public String getRefreshCookieSameSite() {
        return refreshCookieSameSite;
    }

    public void setRefreshCookieSameSite(String refreshCookieSameSite) {
        this.refreshCookieSameSite = refreshCookieSameSite;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}

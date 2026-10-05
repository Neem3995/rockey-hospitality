package com.rockey.hospitality.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rockey.hospitality.configuration.SecurityProperties;
import com.rockey.hospitality.dto.auth.AuthResponse;
import com.rockey.hospitality.dto.auth.CurrentUserResponse;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.UserStatus;
import com.rockey.hospitality.exception.GlobalExceptionHandler;
import com.rockey.hospitality.exception.InvalidCredentialsException;
import com.rockey.hospitality.exception.RateLimitExceededException;
import com.rockey.hospitality.service.AuthService;
import com.rockey.hospitality.service.AuthSession;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;
    private SecurityProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SecurityProperties();
        properties.setRefreshTokenDays(7);
        properties.setRefreshCookieSecure(false);
        properties.setRefreshCookieSameSite("Lax");

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(new AuthController(authService, properties))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.destroy();
    }

    @Test
    void registrationReturnsUserOnlyResponseAndHttpOnlyRefreshCookie() throws Exception {
        when(authService.register(
                "Guest User",
                "guest@example.test",
                "valid-password",
                "127.0.0.1"
        )).thenReturn(session());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Guest User",
                                  "email": "guest@example.test",
                                  "password": "valid-password"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void roleEscalationFieldIsIgnoredAndCannotReachService() throws Exception {
        when(authService.register(
                "Guest User",
                "guest@example.test",
                "valid-password",
                "127.0.0.1"
        )).thenReturn(session());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Guest User",
                                  "email": "guest@example.test",
                                  "password": "valid-password",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("USER"));

        verify(authService).register(
                "Guest User",
                "guest@example.test",
                "valid-password",
                "127.0.0.1"
        );
    }

    @Test
    void invalidRegistrationReturnsCanonicalValidationError() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "G",
                                  "email": "not-an-email",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void loginReturnsAccessResponseAndReplacesRefreshCookie() throws Exception {
        when(authService.login(
                "guest@example.test",
                "valid-password",
                "127.0.0.1"
        )).thenReturn(session());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "guest@example.test",
                                  "password": "valid-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("rockey_refresh=raw-refresh-token")
                ))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void refreshReadsOpaqueTokenOnlyFromCookie() throws Exception {
        when(authService.refresh("old-refresh-token", "127.0.0.1"))
                .thenReturn(session());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("rockey_refresh", "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));

        verify(authService).refresh("old-refresh-token", "127.0.0.1");
    }

    @Test
    void refreshAcceptsTrustedBrowserOriginAndPreservesCookiePolicy() throws Exception {
        when(authService.refresh("old-refresh-token", "127.0.0.1")).thenReturn(session());
        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .cookie(new Cookie("rockey_refresh", "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
        verify(authService).refresh("old-refresh-token", "127.0.0.1");
    }

    @Test
    void refreshUsesConfiguredProductionOriginAndSecureFlag() throws Exception {
        properties.setAllowedOrigins(List.of("https://approved.example.test"));
        properties.setRefreshCookieSecure(true);
        when(authService.refresh("old-refresh-token", "127.0.0.1")).thenReturn(session());
        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "https://approved.example.test")
                        .cookie(new Cookie("rockey_refresh", "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://unapproved.example.test", "null", "http://localhost:5174",
            "http://localhost:5173.unapproved.example.test", "http://localhost:5173/", "",
            "http://localhost:5173, https://unapproved.example.test"})
    void refreshRejectsUntrustedOrMalformedOriginBeforeRotation(String origin) throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, origin)
                        .cookie(new Cookie("rockey_refresh", "old-refresh-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Refresh origin is not allowed."));
        verifyNoInteractions(authService);
    }

    @Test
    void refreshRejectsDuplicateOriginHeaders() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173", "http://localhost:5173")
                        .cookie(new Cookie("rockey_refresh", "old-refresh-token")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @Test
    void invalidCredentialsReturnCanonical401WithoutAccountDetails() throws Exception {
        when(authService.login(
                "missing@example.test",
                "wrong-password",
                "127.0.0.1"
        )).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "missing@example.test",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void rateLimitReturnsCanonical429() throws Exception {
        when(authService.refresh(null, "127.0.0.1"))
                .thenThrow(new RateLimitExceededException(
                        "Too many refresh attempts. Try again later."
                ));

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"));
    }

    private AuthSession session() {
        CurrentUserResponse user = new CurrentUserResponse(
                1L,
                "Guest User",
                "guest@example.test",
                Role.USER,
                null,
                UserStatus.ACTIVE,
                null
        );
        AuthResponse response = new AuthResponse(
                "access-token",
                "Bearer",
                Instant.parse("2026-10-03T20:15:00Z"),
                Instant.parse("2026-10-10T20:00:00Z"),
                user
        );
        return new AuthSession(response, "raw-refresh-token");
    }
}

package com.rockey.hospitality.configuration;

import com.rockey.hospitality.controller.*;
import com.rockey.hospitality.dto.AuthDtos.*;
import com.rockey.hospitality.dto.UserDtos.*;
import com.rockey.hospitality.dto.RoomDtos.*;
import com.rockey.hospitality.dto.TaskDtos.*;
import com.rockey.hospitality.entity.*;
import com.rockey.hospitality.exception.ApiException.*;
import com.rockey.hospitality.repository.UserRepository;
import com.rockey.hospitality.security.*;
import com.rockey.hospitality.security.SecurityHandlers.*;
import com.rockey.hospitality.service.*;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, UserController.class, RoomController.class, TaskController.class})
@Import({ApplicationConfiguration.class, SecurityConfiguration.class, JwtAuthenticationFilter.class, JwtService.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, SecurityErrorWriter.class})
@org.springframework.boot.context.properties.EnableConfigurationProperties(SecurityProperties.class)
class HousekeepingApiTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean UserRepository accounts;
    @MockitoBean AuthService auth;
    @MockitoBean UserService users;
    @MockitoBean RoomService rooms;
    @MockitoBean TaskService tasks;
    private final LocalDateTime time = LocalDateTime.of(2026, 7, 1, 9, 0);

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        registry.add("rockey.security.jwt-secret", () -> Base64.getEncoder().encodeToString(key));
        registry.add("rockey.security.allowed-origins", () -> "http://localhost:5173");
    }
    private User account(User.Role role) {
        User user = new User("Test account", role.name().toLowerCase() + "@example.test", "unused-test-hash", role);
        ReflectionTestUtils.setField(user, "id", 1L); return user;
    }
    private String bearer(User user) {
        when(accounts.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        return "Bearer " + jwt.issueAccessToken(user).getValue();
    }
    private String bearer(User.Role role) { return bearer(account(role)); }
    private RoomResponse room() { return new RoomResponse(4L, "104", 1, Room.Status.DIRTY, true, time, time); }
    private TaskResponse task() {
        return new TaskResponse(5L, "Clean room", "Routine clean", Task.Status.ASSIGNED, Task.Priority.HIGH,
                new UserSummary(1L, "Test housekeeper"), room(), time, null, time, time);
    }
    private AuthService.AuthSession session() {
        return new AuthService.AuthSession(new AuthResponse("synthetic-access", "Bearer", Instant.now().plusSeconds(900),
                Instant.now().plusSeconds(86400), new CurrentUserResponse(1L, "Test account", "user@example.test", User.Role.USER, true)),
                "synthetic-cookie");
    }

    @ParameterizedTest @ValueSource(strings = {"/api/auth/me", "/api/users", "/api/rooms", "/api/tasks", "/api/rooms/1/inspections"})
    void anonymousCannotRead(String path) throws Exception { mvc.perform(get(path)).andExpect(status().isUnauthorized()); }
    @Test void anonymousLogoutDenied() throws Exception { mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized()); }
    @Test void registerOnlyDelegatesCanonicalFieldsAndSetsCookie() throws Exception {
        when(auth.register(anyString(), anyString(), anyString(), anyString())).thenReturn(session());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test person\",\"email\":\"user@example.test\",\"password\":\"synthetic-password\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist()).andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
        verify(auth).register(eq("Test person"), eq("user@example.test"), eq("synthetic-password"), anyString());
    }
    @Test void loginSerializesSafeResponse() throws Exception {
        when(auth.login(anyString(), anyString(), anyString())).thenReturn(session());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.test\",\"password\":\"synthetic-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.active").value(true)).andExpect(jsonPath("$.refreshTokenHash").doesNotExist());
    }
    @Test void invalidLoginHasFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"bad\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists());
        verifyNoInteractions(auth);
    }
    @Test void trustedRefreshSetsCookie() throws Exception {
        when(auth.refresh(anyString(), anyString())).thenReturn(session());
        mvc.perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .cookie(new Cookie("rockey_refresh", "synthetic-cookie")))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")));
    }
    @Test void refreshWithoutOriginRetainsNonBrowserCompatibility() throws Exception {
        when(auth.refresh(isNull(), anyString())).thenReturn(session());
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isOk());
    }
    @Test void untrustedOriginCannotRefresh() throws Exception {
        mvc.perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, "https://untrusted.example.test"))
                .andExpect(status().isForbidden()); verifyNoInteractions(auth);
    }
    @Test void repeatedOriginCannotRefresh() throws Exception {
        mvc.perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, "http://localhost:5173", "http://localhost:5173"))
                .andExpect(status().isForbidden()); verifyNoInteractions(auth);
    }
    @Test void logoutIsBodylessAndExpiresCookie() throws Exception {
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(User.Role.USER)))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        verify(auth).logout(1L);
    }
    @Test void ownProfileUsesPrincipal() throws Exception {
        when(auth.getCurrentUser(1L)).thenReturn(session().getResponse().getUser());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(User.Role.USER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
    @ParameterizedTest @ValueSource(strings = {"/api/users", "/api/users/1", "/api/rooms", "/api/rooms/1", "/api/rooms/1/inspections"})
    void workerCannotReadManagement(String path) throws Exception {
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(User.Role.USER))).andExpect(status().isForbidden());
    }
    @Test void workerCannotCreateTask() throws Exception {
        mvc.perform(post("/api/tasks").header(HttpHeaders.AUTHORIZATION, bearer(User.Role.USER)))
                .andExpect(status().isForbidden()); verifyNoInteractions(tasks);
    }
    @Test void workerReadsArrayWithoutPaginationWrapper() throws Exception {
        when(tasks.list(1L)).thenReturn(List.of(task()));
        mvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, bearer(User.Role.USER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].assignedUser.id").value(1))
                .andExpect(jsonPath("$[0].room.status").value("DIRTY")).andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }
    @Test void workerReadsTaskAndUpdatesStatusOnly() throws Exception {
        when(tasks.get(1L, 5L)).thenReturn(task()); when(tasks.status(1L, 5L, Task.Status.IN_PROGRESS)).thenReturn(task());
        String token = bearer(User.Role.USER);
        mvc.perform(get("/api/tasks/5").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
        mvc.perform(put("/api/tasks/5/status").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isOk());
        verify(tasks).status(1L, 5L, Task.Status.IN_PROGRESS);
    }
    @Test void workerCannotEditOrCancelTask() throws Exception {
        String token = bearer(User.Role.USER);
        mvc.perform(put("/api/tasks/5").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/tasks/5").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
    }
    @Test void managerCannotUpdateOrDeactivateAccounts() throws Exception {
        String token = bearer(User.Role.MANAGER);
        mvc.perform(put("/api/users/2").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/2").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
    }
    @Test void managementAccountCrudUsesSafeDtos() throws Exception {
        UserResponse result = new UserResponse(2L, "Test person", "person@example.test", User.Role.USER, true);
        when(users.list(1L)).thenReturn(List.of(result)); when(users.get(1L, 2L)).thenReturn(result);
        when(users.create(eq(1L), any())).thenReturn(result); when(users.update(eq(1L), eq(2L), any())).thenReturn(result);
        String token = bearer(User.Role.ADMIN);
        mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].active").value(true));
        mvc.perform(get("/api/users/2").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
        mvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test person\",\"email\":\"person@example.test\",\"password\":\"synthetic-password\",\"role\":\"USER\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(put("/api/users/2").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test person\",\"email\":\"person@example.test\",\"role\":\"MANAGER\",\"active\":true}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/users/2").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isNoContent());
    }
    @Test void managerRoomAndInspectionCrud() throws Exception {
        when(rooms.list(1L)).thenReturn(List.of(room())); when(rooms.get(1L,4L)).thenReturn(room());
        when(rooms.create(eq(1L), any())).thenReturn(room()); when(rooms.update(eq(1L),eq(4L),any())).thenReturn(room());
        when(rooms.status(1L,4L,Room.Status.DIRTY)).thenReturn(room());
        InspectionResponse inspection = new InspectionResponse(7L,4L,5L,new UserSummary(1L,"Supervisor"),Inspection.Result.PASS,"Ready",time);
        when(rooms.inspect(eq(1L),eq(4L),any())).thenReturn(inspection); when(rooms.inspectionHistory(1L,4L)).thenReturn(List.of(inspection));
        String token = bearer(User.Role.MANAGER);
        mvc.perform(get("/api/rooms").header(HttpHeaders.AUTHORIZATION,token)).andExpect(status().isOk());
        mvc.perform(get("/api/rooms/4").header(HttpHeaders.AUTHORIZATION,token)).andExpect(status().isOk());
        String input = "{\"roomNumber\":\"104\",\"floor\":1,\"status\":\"DIRTY\",\"active\":true}";
        mvc.perform(post("/api/rooms").header(HttpHeaders.AUTHORIZATION,token).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isCreated());
        mvc.perform(put("/api/rooms/4").header(HttpHeaders.AUTHORIZATION,token).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isOk());
        mvc.perform(put("/api/rooms/4/status").header(HttpHeaders.AUTHORIZATION,token).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DIRTY\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/rooms/4/inspections").header(HttpHeaders.AUTHORIZATION,token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":5,\"result\":\"PASS\",\"notes\":\"Ready\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.inspectedBy.id").value(1)).andExpect(jsonPath("$.result").value("PASS"));
        mvc.perform(get("/api/rooms/4/inspections").header(HttpHeaders.AUTHORIZATION,token)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inspectedAt").exists());
        mvc.perform(delete("/api/rooms/4").header(HttpHeaders.AUTHORIZATION,token)).andExpect(status().isNoContent());
    }
    @Test void managerTaskCrud() throws Exception {
        when(tasks.create(eq(1L), any())).thenReturn(task()); when(tasks.update(eq(1L),eq(5L),any())).thenReturn(task());
        String token = bearer(User.Role.MANAGER);
        String input = "{\"title\":\"Clean room\",\"description\":\"Routine clean\",\"priority\":\"HIGH\",\"assignedUserId\":1,\"roomId\":4,\"dueAt\":\"2026-07-01T09:00:00\"}";
        mvc.perform(post("/api/tasks").header(HttpHeaders.AUTHORIZATION,token).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isCreated());
        mvc.perform(put("/api/tasks/5").header(HttpHeaders.AUTHORIZATION,token).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isOk());
        mvc.perform(delete("/api/tasks/5").header(HttpHeaders.AUTHORIZATION,token)).andExpect(status().isNoContent());
    }
    @ParameterizedTest @ValueSource(strings = {"/api/rooms", "/api/tasks", "/api/users", "/api/rooms/4/inspections"})
    void managementValidationRejectsEmptyBody(String path) throws Exception {
        mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION,bearer(User.Role.ADMIN)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").exists());
    }
    @Test void enumAndIdParsingAreSafe() throws Exception {
        String token = bearer(User.Role.MANAGER);
        mvc.perform(put("/api/tasks/5/status").header(HttpHeaders.AUTHORIZATION,token).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INVALID\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/rooms/not-an-id").header(HttpHeaders.AUTHORIZATION,token)).andExpect(status().isBadRequest());
    }
    @Test void missingConflictForbiddenAndUnexpectedErrorsUseCanonicalShape() throws Exception {
        String token = bearer(User.Role.MANAGER);
        when(rooms.get(1L,4L)).thenThrow(new ResourceNotFoundException("Room not found."));
        mvc.perform(get("/api/rooms/4").header(HttpHeaders.AUTHORIZATION,token)).andExpect(status().isNotFound()).andExpect(jsonPath("$.path").value("/api/rooms/4"));
        doThrow(new ConflictException("Active work.")).when(rooms).get(1L,4L);
        mvc.perform(get("/api/rooms/4").header(HttpHeaders.AUTHORIZATION,token)).andExpect(status().isConflict());
        doThrow(new ForbiddenException("Not allowed.")).when(rooms).get(1L,4L);
        mvc.perform(get("/api/rooms/4").header(HttpHeaders.AUTHORIZATION,token)).andExpect(status().isForbidden());
        doThrow(new IllegalStateException("private-test-value")).when(rooms).get(1L,4L);
        mvc.perform(get("/api/rooms/4").header(HttpHeaders.AUTHORIZATION,token)).andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("private-test-value"))));
    }
    @Test void tokenRoleAndAccountActivationAreDatabaseAuthoritative() throws Exception {
        User user = account(User.Role.USER); String token = bearer(user);
        user.update(user.getName(), user.getEmail(), User.Role.MANAGER, true);
        mvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION,token)).andExpect(status().isUnauthorized());
        token = bearer(user); user.deactivate();
        mvc.perform(get("/api/rooms").header(HttpHeaders.AUTHORIZATION,token)).andExpect(status().isUnauthorized());
    }
    @Test void malformedBearerAndDeletedAccountAreRejected() throws Exception {
        mvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION,"Basic invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION,"Bearer invalid")).andExpect(status().isUnauthorized());
        User user = account(User.Role.USER); String token = bearer(user);
        when(accounts.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.empty());
        mvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION,token)).andExpect(status().isUnauthorized());
    }
    @Test void credentialedCorsAndSecurityHeaders() throws Exception {
        mvc.perform(options("/api/tasks").header(HttpHeaders.ORIGIN,"http://localhost:5173")
                .header("Access-Control-Request-Method","GET")).andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials","true"))
                .andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
        mvc.perform(options("/api/tasks").header(HttpHeaders.ORIGIN,"https://untrusted.example.test")
                .header("Access-Control-Request-Method","GET")).andExpect(status().isForbidden());
        mvc.perform(get("/api/tasks")).andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(header().string("X-Frame-Options","DENY"));
    }
    @Test void undocumentedPathsAreDenied() throws Exception {
        mvc.perform(get("/api/unapproved").header(HttpHeaders.AUTHORIZATION,bearer(User.Role.ADMIN))).andExpect(status().isForbidden());
    }
}

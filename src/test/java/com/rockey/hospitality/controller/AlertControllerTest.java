package com.rockey.hospitality.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rockey.hospitality.dto.AlertDtos.AlertEmployeeSummary;
import com.rockey.hospitality.dto.AlertDtos.AlertResponse;
import com.rockey.hospitality.dto.AlertDtos.AlertSearchCriteria;
import com.rockey.hospitality.dto.AlertDtos.AlertTaskSummary;
import com.rockey.hospitality.dto.CommonDtos.PageCriteria;
import com.rockey.hospitality.dto.CommonDtos.PagedResponse;
import com.rockey.hospitality.entity.Alert;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.exception.ApiException.ConflictException;
import com.rockey.hospitality.exception.ApiException.ForbiddenException;
import com.rockey.hospitality.exception.ApiException.ResourceNotFoundException;
import com.rockey.hospitality.exception.GlobalExceptionHandler;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.AlertService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class AlertControllerTest {
    @Mock private AlertService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = standaloneSetup(new AlertController(service)).setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        User user = new User("Worker", "worker@example.test", "hash");
        ReflectionTestUtils.setField(user, "id", 21L); ReflectionTestUtils.setField(user, "role", User.Role.STAFF);
        RockeyUserPrincipal principal = new RockeyUserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    @Test
    void defaultListPassesPrincipalAndCanonicalPagination() throws Exception {
        when(service.listAlerts(new AlertSearchCriteria(null, null, null), new PageCriteria(0, 20, "createdAt,desc"), 21L, User.Role.STAFF))
                .thenReturn(new PagedResponse<>(List.of(response()), 0, 20, 1, 1, true));
        mvc.perform(get("/api/alerts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("TASK"))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void listPassesAllFiltersAndSort() throws Exception {
        when(service.listAlerts(new AlertSearchCriteria(12L, Alert.Type.TASK, Alert.Status.READ), new PageCriteria(1, 5, "createdAt,asc"), 21L, User.Role.STAFF))
                .thenReturn(new PagedResponse<>(List.of(), 1, 5, 0, 0, true));
        mvc.perform(get("/api/alerts").param("employeeId", "12").param("type", "TASK").param("status", "READ")
                        .param("page", "1").param("size", "5").param("sort", "createdAt,asc"))
                .andExpect(status().isOk());
        verify(service).listAlerts(new AlertSearchCriteria(12L, Alert.Type.TASK, Alert.Status.READ), new PageCriteria(1, 5, "createdAt,asc"), 21L, User.Role.STAFF);
    }

    @Test
    void detailReturnsSafeDtoAndShallowEmployeeTaskSummaries() throws Exception {
        when(service.getAlert(501L, 21L, User.Role.STAFF)).thenReturn(response());
        mvc.perform(get("/api/alerts/501")).andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.id").value(12)).andExpect(jsonPath("$.task.id").value(10))
                .andExpect(jsonPath("$.sourceKey").value("TASK:10:OVERDUE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshTokenHash").doesNotExist()).andExpect(jsonPath("$.employee.user").doesNotExist())
                .andExpect(jsonPath("$.task.assignedEmployee").doesNotExist());
    }

    @Test
    void markReadNeedsNoBodyAndReturns200() throws Exception {
        when(service.markRead(501L, 21L, User.Role.STAFF)).thenReturn(response());
        mvc.perform(put("/api/alerts/501/read")).andExpect(status().isOk());
        verify(service).markRead(501L, 21L, User.Role.STAFF);
    }

    @Test
    void deleteResolvesAndReturns204WithoutBody() throws Exception {
        mvc.perform(delete("/api/alerts/501")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).resolveAlert(501L, 21L, User.Role.STAFF);
    }

    @ParameterizedTest
    @ValueSource(strings = {"type", "status", "employeeId", "page"})
    void malformedFiltersUseCanonical400(String parameter) throws Exception {
        mvc.perform(get("/api/alerts").param(parameter, "INVALID"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void missingAlertUses404Contract() throws Exception {
        when(service.getAlert(99L, 21L, User.Role.STAFF)).thenThrow(new ResourceNotFoundException("Alert not found."));
        mvc.perform(get("/api/alerts/99")).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void otherRecipientUses403Contract() throws Exception {
        when(service.markRead(501L, 21L, User.Role.STAFF)).thenThrow(new ForbiddenException("Another employee's alert."));
        mvc.perform(put("/api/alerts/501/read")).andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void resolvedReadUses409Contract() throws Exception {
        when(service.markRead(501L, 21L, User.Role.STAFF)).thenThrow(new ConflictException("Alert already resolved."));
        mvc.perform(put("/api/alerts/501/read")).andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    private AlertResponse response() {
        return new AlertResponse(501L, Alert.Type.TASK, "Task is overdue.", Alert.Severity.INFO, Alert.Status.UNREAD,
                new AlertEmployeeSummary(12L, "Worker"), new AlertTaskSummary(10L, "Inspect room"),
                "TASK:10:OVERDUE", LocalDateTime.of(2030, 1, 1, 12, 0), null, null);
    }
}

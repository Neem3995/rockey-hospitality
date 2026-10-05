package com.rockey.hospitality.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rockey.hospitality.dto.auth.DepartmentSummary;
import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.dto.inventory.CreateInventoryItemRequest;
import com.rockey.hospitality.dto.inventory.InventoryItemResponse;
import com.rockey.hospitality.dto.inventory.UpdateInventoryItemRequest;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.exception.BadRequestException;
import com.rockey.hospitality.exception.ConflictException;
import com.rockey.hospitality.exception.ForbiddenException;
import com.rockey.hospitality.exception.GlobalExceptionHandler;
import com.rockey.hospitality.exception.ResourceNotFoundException;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    @Mock private InventoryService service;
    private MockMvc mvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new InventoryController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
        authenticate(Role.ADMIN);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        validator.destroy();
    }

    @Test
    void listPassesFiltersPageSortAndAuthenticatedIdentity() throws Exception {
        authenticate(Role.STAFF);
        when(service.listInventory(3L, true, 1, 5, "quantity,desc", 21L, Role.STAFF))
                .thenReturn(new PagedResponse<>(List.of(response()), 1, 5, 6, 2, true));
        mvc.perform(get("/api/inventory").param("departmentId", "3").param("active", "true")
                        .param("page", "1").param("size", "5").param("sort", "quantity,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("HK-TOWEL-BATH"))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void defaultListUsesCanonicalPageDefaults() throws Exception {
        when(service.listInventory(null, null, 0, 20, "name,asc", 3L, Role.ADMIN))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0, true));
        mvc.perform(get("/api/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        verify(service).listInventory(null, null, 0, 20, "name,asc", 3L, Role.ADMIN);
    }

    @Test
    void createReturns201AndOnlyShallowSafeDtoFields() throws Exception {
        when(service.createInventoryItem(any(CreateInventoryItemRequest.class))).thenReturn(response());
        mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON).content(createJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(88))
                .andExpect(jsonPath("$.quantity").value(18))
                .andExpect(jsonPath("$.reorderThreshold").value(20))
                .andExpect(jsonPath("$.department.id").value(3))
                .andExpect(jsonPath("$.department.employees").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshTokenHash").doesNotExist());
    }

    @Test
    void getUsesCurrentUserAndRole() throws Exception {
        authenticate(Role.STAFF);
        when(service.getInventoryItem(88L, 21L, Role.STAFF)).thenReturn(response());
        mvc.perform(get("/api/inventory/88"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sku").value("HK-TOWEL-BATH"));
        verify(service).getInventoryItem(88L, 21L, Role.STAFF);
    }

    @Test
    void putRestockReturns200() throws Exception {
        when(service.updateInventoryItem(eq(88L), any(UpdateInventoryItemRequest.class)))
                .thenReturn(response());
        mvc.perform(put("/api/inventory/88").contentType(MediaType.APPLICATION_JSON).content(updateJson()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void deleteReturns204WithNoBody() throws Exception {
        mvc.perform(delete("/api/inventory/88"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).deactivateInventoryItem(88L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\"X\",\"sku\":\"TEST\",\"departmentId\":3}",
            "{\"name\":\"Towels\",\"sku\":\"\",\"departmentId\":3}",
            "{\"name\":\"Towels\",\"sku\":\"TEST\",\"quantity\":-1,\"departmentId\":3}",
            "{\"name\":\"Towels\",\"sku\":\"TEST\",\"reorderThreshold\":-1,\"departmentId\":3}",
            "{\"name\":\"Towels\",\"sku\":\"TEST\",\"quantity\":null,\"departmentId\":3}",
            "{\"name\":\"Towels\",\"sku\":\"TEST\",\"departmentId\":0}",
            "{\"name\":\"Towels\",\"sku\":\"TEST\"}"
    })
    void invalidCreateReturnsCanonical400WithoutCallingService(String json) throws Exception {
        mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isMap());
        verifyNoInteractions(service);
    }

    @Test
    void missingUpdateFieldsAreRejected() throws Exception {
        mvc.perform(put("/api/inventory/88").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Towels\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantity").exists())
                .andExpect(jsonPath("$.fieldErrors.reorderThreshold").exists())
                .andExpect(jsonPath("$.fieldErrors.departmentId").exists())
                .andExpect(jsonPath("$.fieldErrors.active").exists());
        verifyNoInteractions(service);
    }

    @Test
    void malformedFilterReturns400() throws Exception {
        mvc.perform(get("/api/inventory").param("active", "invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void invalidSkuServiceErrorUsesCanonical400() throws Exception {
        when(service.createInventoryItem(any(CreateInventoryItemRequest.class)))
                .thenThrow(new BadRequestException("SKU is invalid."));
        mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON).content(createJson()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void duplicateSkuUsesCanonical409() throws Exception {
        when(service.createInventoryItem(any(CreateInventoryItemRequest.class)))
                .thenThrow(new ConflictException("Inventory SKU is already registered."));
        mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON).content(createJson()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void missingItemUsesCanonical404() throws Exception {
        when(service.getInventoryItem(99L, 3L, Role.ADMIN))
                .thenThrow(new ResourceNotFoundException("Inventory item not found with id 99."));
        mvc.perform(get("/api/inventory/99"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void crossDepartmentReadUsesCanonical403() throws Exception {
        authenticate(Role.STAFF);
        when(service.getInventoryItem(88L, 21L, Role.STAFF))
                .thenThrow(new ForbiddenException("Inventory access is forbidden."));
        mvc.perform(get("/api/inventory/88"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    private void authenticate(Role role) {
        User user = new User("Test User", "inventory@example.test", "hash");
        ReflectionTestUtils.setField(user, "id", role == Role.STAFF ? 21L : 3L);
        ReflectionTestUtils.setField(user, "role", role);
        RockeyUserPrincipal principal = new RockeyUserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private String createJson() {
        return """
                {"name":"Bath Towels","sku":"HK-TOWEL-BATH","quantity":18,
                 "reorderThreshold":20,"departmentId":3}
                """;
    }

    private String updateJson() {
        return """
                {"name":"Bath Towels","quantity":18,"reorderThreshold":20,
                 "departmentId":3,"active":true}
                """;
    }

    private InventoryItemResponse response() {
        return new InventoryItemResponse(88L, "Bath Towels", "HK-TOWEL-BATH", 18, 20,
                new DepartmentSummary(3L, "Housekeeping"), true,
                LocalDateTime.of(2026, 10, 3, 9, 0), LocalDateTime.of(2026, 10, 3, 10, 0));
    }
}

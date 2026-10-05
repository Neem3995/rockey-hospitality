package com.rockey.hospitality.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rockey.hospitality.entity.Department;
import com.rockey.hospitality.exception.ConflictException;
import com.rockey.hospitality.exception.GlobalExceptionHandler;
import com.rockey.hospitality.exception.ResourceNotFoundException;
import com.rockey.hospitality.service.DepartmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
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
class DepartmentControllerTest {

    @Mock
    private DepartmentService departmentService;

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(new DepartmentController(departmentService))
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
    void listDepartmentsReturnsResponsesAndPassesActiveFilter() throws Exception {
        when(departmentService.listDepartments(true))
                .thenReturn(List.of(department(1L, "Housekeeping", true)));

        mockMvc.perform(get("/api/departments").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Housekeeping"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].createdAt").value("2026-10-03T12:00:00"));

        verify(departmentService).listDepartments(true);
    }

    @Test
    void createDepartmentReturnsCreatedResponse() throws Exception {
        when(departmentService.createDepartment("Housekeeping", "Room operations"))
                .thenReturn(department(1L, "Housekeeping", true));

        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Housekeeping",
                                  "description": "Room operations"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Housekeeping"));

        verify(departmentService).createDepartment("Housekeeping", "Room operations");
    }

    @Test
    void getDepartmentReturnsExistingDepartment() throws Exception {
        when(departmentService.getDepartment(1L))
                .thenReturn(department(1L, "Housekeeping", true));

        mockMvc.perform(get("/api/departments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Housekeeping"));
    }

    @Test
    void updateDepartmentReturnsUpdatedDepartment() throws Exception {
        Department updated = department(1L, "Guest Services", true);
        when(departmentService.updateDepartment(1L, "Guest Services", "Front desk"))
                .thenReturn(updated);

        mockMvc.perform(put("/api/departments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Guest Services",
                                  "description": "Front desk"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Guest Services"));
    }

    @Test
    void deactivateDepartmentReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/departments/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(departmentService).deactivateDepartment(1L);
    }

    @Test
    void invalidCreateReturnsStructuredValidationError() throws Exception {
        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "description": "Valid description"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed."))
                .andExpect(jsonPath("$.path").value("/api/departments"))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void missingDepartmentReturnsStructuredNotFoundError() throws Exception {
        when(departmentService.getDepartment(99L))
                .thenThrow(new ResourceNotFoundException("Department not found with id 99."));

        mockMvc.perform(get("/api/departments/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Department not found with id 99."))
                .andExpect(jsonPath("$.path").value("/api/departments/99"));
    }

    @Test
    void duplicateDepartmentReturnsStructuredConflictError() throws Exception {
        when(departmentService.createDepartment("Housekeeping", null))
                .thenThrow(new ConflictException("Department name already exists."));

        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Housekeeping"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Department name already exists."));
    }

    @Test
    void invalidActiveFilterReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/departments").param("active", "not-a-boolean"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request could not be read."));
    }

    private Department department(Long id, String name, boolean active) {
        Department department = new Department(name, "Department description");
        ReflectionTestUtils.setField(department, "id", id);
        ReflectionTestUtils.setField(department, "active", active);
        ReflectionTestUtils.setField(
                department,
                "createdAt",
                LocalDateTime.of(2026, 10, 3, 12, 0)
        );
        ReflectionTestUtils.setField(
                department,
                "updatedAt",
                LocalDateTime.of(2026, 10, 3, 12, 0)
        );
        return department;
    }
}

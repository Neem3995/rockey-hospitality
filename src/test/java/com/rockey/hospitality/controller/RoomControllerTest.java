package com.rockey.hospitality.controller;

import com.rockey.hospitality.dto.common.PageCriteria;
import com.rockey.hospitality.dto.room.RoomSearchCriteria;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.dto.room.CreateRoomRequest;
import com.rockey.hospitality.dto.room.RoomResponse;
import com.rockey.hospitality.dto.room.UpdateRoomRequest;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.RoomStatus;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.exception.GlobalExceptionHandler;
import com.rockey.hospitality.exception.ResourceNotFoundException;
import com.rockey.hospitality.security.RockeyUserPrincipal;
import com.rockey.hospitality.service.RoomService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    @Mock
    private RoomService roomService;

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = standaloneSetup(new RoomController(roomService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        validator.destroy();
    }

    @Test
    void listRoomsReturnsPageAndPassesCanonicalFilters() throws Exception {
        when(roomService.listRooms(new RoomSearchCriteria(RoomStatus.DIRTY, 2, "STANDARD", true), new PageCriteria(1, 5, "createdAt,desc"), Role.STAFF)).thenReturn(new PagedResponse<>(List.of(response()), 1, 5, 6, 2, true));

        mockMvc.perform(get("/api/rooms")
                        .principal(authentication(21L, Role.STAFF))
                        .param("status", "DIRTY")
                        .param("floor", "2")
                        .param("type", "STANDARD")
                        .param("active", "true")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].roomNumber").value("218"))
                .andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    void createRoomReturns201AndNonSensitiveDto() throws Exception {
        when(roomService.createRoom(any(CreateRoomRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.status").value("DIRTY"))
                .andExpect(jsonPath("$.booking").doesNotExist())
                .andExpect(jsonPath("$.guest").doesNotExist());
    }

    @Test
    void invalidCreateReturnsCanonical400() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomNumber": "218",
                                  "roomType": "S",
                                  "floor": 0,
                                  "initialStatus": "READY"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.floor").exists())
                .andExpect(jsonPath("$.fieldErrors.roomType").exists());
    }

    @Test
    void getRoomPassesRequesterRole() throws Exception {
        when(roomService.getRoom(12L, Role.ADMIN)).thenReturn(response());

        mockMvc.perform(get("/api/rooms/12")
                        .principal(authentication(3L, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12));

        verify(roomService).getRoom(12L, Role.ADMIN);
    }

    @Test
    void updateRoomReturnsUpdatedDto() throws Exception {
        when(roomService.updateRoom(any(Long.class), any(UpdateRoomRequest.class)))
                .thenReturn(response());

        mockMvc.perform(put("/api/rooms/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomType": "STANDARD",
                                  "floor": 2,
                                  "nextArrivalAt": "2030-10-03T15:00:00",
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextArrivalAt").value("2030-10-03T15:00:00"));
    }

    @Test
    void deactivateRoomReturns204() throws Exception {
        mockMvc.perform(delete("/api/rooms/12"))
                .andExpect(status().isNoContent());

        verify(roomService).deactivateRoom(12L);
    }

    @Test
    void patchStatusPassesAuthenticatedIdentityAndReturnsRoom() throws Exception {
        when(roomService.updateStatus(
                12L,
                RoomStatus.CLEANING,
                21L,
                Role.STAFF
        )).thenReturn(response());

        mockMvc.perform(patch("/api/rooms/12/status")
                        .principal(authentication(21L, Role.STAFF))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CLEANING"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomNumber").value("218"));

        verify(roomService).updateStatus(
                12L,
                RoomStatus.CLEANING,
                21L,
                Role.STAFF
        );
    }

    @Test
    void invalidStatusValueReturnsCanonical400() throws Exception {
        mockMvc.perform(patch("/api/rooms/12/status")
                        .principal(authentication(21L, Role.STAFF))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"AVAILABLE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void missingRoomReturnsCanonical404() throws Exception {
        when(roomService.getRoom(99L, Role.ADMIN))
                .thenThrow(new ResourceNotFoundException("Room not found with id 99."));

        mockMvc.perform(get("/api/rooms/99")
                        .principal(authentication(3L, Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private String validCreateJson() {
        return """
                {
                  "roomNumber": "218",
                  "roomType": "STANDARD",
                  "floor": 2,
                  "initialStatus": "DIRTY",
                  "nextArrivalAt": "2030-10-03T15:00:00"
                }
                """;
    }

    private UsernamePasswordAuthenticationToken authentication(Long id, Role role) {
        User user = new User("Test User", role.name().toLowerCase() + "@example.test", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", role);
        RockeyUserPrincipal principal = new RockeyUserPrincipal(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    private RoomResponse response() {
        return new RoomResponse(
                12L,
                "218",
                "STANDARD",
                RoomStatus.DIRTY,
                2,
                LocalDateTime.of(2030, 10, 3, 15, 0),
                true,
                LocalDateTime.of(2026, 10, 3, 9, 0),
                LocalDateTime.of(2026, 10, 3, 10, 0)
        );
    }
}

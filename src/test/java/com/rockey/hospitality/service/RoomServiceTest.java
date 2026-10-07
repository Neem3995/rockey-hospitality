package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.CommonDtos.PageCriteria;
import com.rockey.hospitality.dto.CommonDtos.PagedResponse;
import com.rockey.hospitality.dto.RoomDtos.CreateRoomRequest;
import com.rockey.hospitality.dto.RoomDtos.RoomResponse;
import com.rockey.hospitality.dto.RoomDtos.RoomSearchCriteria;
import com.rockey.hospitality.dto.RoomDtos.UpdateRoomRequest;
import com.rockey.hospitality.entity.Department;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.Room;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.exception.ApiException.BadRequestException;
import com.rockey.hospitality.exception.ApiException.ConflictException;
import com.rockey.hospitality.exception.ApiException.ForbiddenException;
import com.rockey.hospitality.exception.ApiException.ResourceNotFoundException;
import com.rockey.hospitality.repository.EmployeeRepository;
import com.rockey.hospitality.repository.RoomRepository;
import com.rockey.hospitality.repository.TaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private TaskRepository taskRepository;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomRepository, employeeRepository, taskRepository);
    }

    @Test
    void createNormalizesUniqueRoomNumberAndDefaultsStatusToReady() {
        CreateRoomRequest request = createRequest();
        request.setRoomNumber(" ab-12 ");
        request.setInitialStatus(null);
        when(roomRepository.existsByRoomNumberIgnoreCase("AB-12")).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            ReflectionTestUtils.setField(room, "id", 12L);
            return room;
        });

        RoomResponse response = roomService.createRoom(request);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        assertThat(captor.getValue().getRoomNumber()).isEqualTo("AB-12");
        assertThat(response.getStatus()).isEqualTo(Room.Status.READY);
        assertThat(response.getRoomType()).isEqualTo("STANDARD");
        assertThat(response.getNextArrivalAt()).isEqualTo(request.getNextArrivalAt());
    }

    @Test
    void createRejectsDuplicateNormalizedNumber() {
        CreateRoomRequest request = createRequest();
        when(roomRepository.existsByRoomNumberIgnoreCase("218")).thenReturn(true);

        assertThatThrownBy(() -> roomService.createRoom(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Room number is already registered.");

        verify(roomRepository, never()).save(any());
    }

    @Test
    void createRejectsPastNextArrival() {
        CreateRoomRequest request = createRequest();
        request.setNextArrivalAt(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> roomService.createRoom(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Next arrival time must be current or future.");

        verify(roomRepository, never()).save(any());
    }

    @Test
    void adminListPassesAllFiltersAndReturnsCanonicalPageMetadata() {
        Room room = room(12L, Room.Status.DIRTY, true);
        PageRequest pageRequest = PageRequest.of(
                1,
                5,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        when(roomRepository.search(
                Room.Status.DIRTY,
                2,
                "Standard",
                false,
                pageRequest
        )).thenReturn(new PageImpl<>(List.of(room), pageRequest, 7));

        PagedResponse<RoomResponse> response = roomService.listRooms(new RoomSearchCriteria(Room.Status.DIRTY, 2, " Standard ", false), new PageCriteria(1, 5, "createdAt,desc"), User.Role.ADMIN);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getTotalElements()).isEqualTo(6);
        assertThat(response.getTotalPages()).isEqualTo(2);
    }

    @Test
    void staffListForcesActiveRoomFilter() {
        PageRequest pageRequest = PageRequest.of(
                0,
                20,
                Sort.by(Sort.Direction.ASC, "roomNumber")
        );
        when(roomRepository.search(null, null, null, true, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        roomService.listRooms(new RoomSearchCriteria(null, null, null, null), new PageCriteria(0, 20, "roomNumber,asc"), User.Role.STAFF);

        verify(roomRepository).search(null, null, null, true, pageRequest);
    }

    @Test
    void staffCannotRequestInactiveRooms() {
        RoomSearchCriteria filterCriteria13 = new RoomSearchCriteria(null, null, null, false);
        PageCriteria paginationCriteria14 = new PageCriteria(0, 20, "roomNumber,asc");
        assertThatThrownBy(() -> roomService.listRooms(filterCriteria13, paginationCriteria14, User.Role.STAFF)).isInstanceOf(ForbiddenException.class);

        verify(roomRepository, never()).search(any(), any(), any(), any(), any());
    }

    @Test
    void listRejectsInvalidFiltersPaginationAndSort() {
        RoomSearchCriteria filterCriteria11 = new RoomSearchCriteria(null, 0, null, null);
        PageCriteria paginationCriteria12 = new PageCriteria(0, 20, "roomNumber,asc");
        assertThatThrownBy(() -> roomService.listRooms(filterCriteria11, paginationCriteria12, User.Role.ADMIN)).isInstanceOf(BadRequestException.class);
        RoomSearchCriteria filterCriteria9 = new RoomSearchCriteria(null, null, " ", null);
        PageCriteria paginationCriteria10 = new PageCriteria(0, 20, "roomNumber,asc");
        assertThatThrownBy(() -> roomService.listRooms(filterCriteria9, paginationCriteria10, User.Role.ADMIN)).isInstanceOf(BadRequestException.class);
        RoomSearchCriteria filterCriteria7 = new RoomSearchCriteria(null, null, null, null);
        PageCriteria paginationCriteria8 = new PageCriteria(-1, 20, "roomNumber,asc");
        assertThatThrownBy(() -> roomService.listRooms(filterCriteria7, paginationCriteria8, User.Role.ADMIN)).isInstanceOf(BadRequestException.class);
        RoomSearchCriteria filterCriteria5 = new RoomSearchCriteria(null, null, null, null);
        PageCriteria paginationCriteria6 = new PageCriteria(0, 101, "roomNumber,asc");
        assertThatThrownBy(() -> roomService.listRooms(filterCriteria5, paginationCriteria6, User.Role.ADMIN)).isInstanceOf(BadRequestException.class);
        RoomSearchCriteria filterCriteria3 = new RoomSearchCriteria(null, null, null, null);
        PageCriteria paginationCriteria4 = new PageCriteria(0, 20, "passwordHash,asc");
        assertThatThrownBy(() -> roomService.listRooms(filterCriteria3, paginationCriteria4, User.Role.ADMIN)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void getAllowsStaffToViewActiveRoom() {
        when(roomRepository.findById(12L))
                .thenReturn(Optional.of(room(12L, Room.Status.READY, true)));

        RoomResponse response = roomService.getRoom(12L, User.Role.STAFF);

        assertThat(response.getId()).isEqualTo(12L);
        assertThat(response.getActive()).isTrue();
    }

    @Test
    void getRejectsInactiveRoomForStaffButAllowsAdmin() {
        Room room = room(12L, Room.Status.OUT_OF_SERVICE, false);
        when(roomRepository.findById(12L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.getRoom(12L, User.Role.STAFF))
                .isInstanceOf(ForbiddenException.class);

        RoomResponse adminResponse = roomService.getRoom(12L, User.Role.ADMIN);
        assertThat(adminResponse.getActive()).isFalse();
    }

    @Test
    void getRejectsMissingRoom() {
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getRoom(99L, User.Role.ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateChangesDetailsArrivalAndActiveStateWithoutChangingStatus() {
        Room room = room(12L, Room.Status.MAINTENANCE, false);
        UpdateRoomRequest request = updateRequest();
        request.setActive(true);
        when(roomRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(room));
        when(roomRepository.save(room)).thenReturn(room);

        RoomResponse response = roomService.updateRoom(12L, request);

        assertThat(response.getRoomType()).isEqualTo("DELUXE");
        assertThat(response.getFloor()).isEqualTo(3);
        assertThat(response.getNextArrivalAt()).isEqualTo(request.getNextArrivalAt());
        assertThat(response.getActive()).isTrue();
        assertThat(response.getStatus()).isEqualTo(Room.Status.MAINTENANCE);
    }

    @Test
    void updateRejectsPastNextArrival() {
        UpdateRoomRequest request = updateRequest();
        request.setNextArrivalAt(LocalDateTime.now().minusSeconds(1));

        assertThatThrownBy(() -> roomService.updateRoom(12L, request))
                .isInstanceOf(BadRequestException.class);

        verify(roomRepository, never()).findById(any());
    }

    @Test
    void deactivateIsIdempotentSoftLifecycleChange() {
        Room room = room(12L, Room.Status.READY, true);
        when(roomRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(room));
        when(roomRepository.save(room)).thenReturn(room);

        roomService.deactivateRoom(12L);
        roomService.deactivateRoom(12L);

        assertThat(room.getActive()).isFalse();
        verify(roomRepository, never()).delete(any());
    }

    @Test
    void canonicalTurnoverSequenceReachesReadyWithoutBookingData() {
        Room room = room(12L, Room.Status.OCCUPIED, true);
        when(roomRepository.findById(12L)).thenReturn(Optional.of(room));
        when(roomRepository.save(room)).thenReturn(room);

        roomService.updateStatus(12L, Room.Status.DIRTY, 3L, User.Role.ADMIN);
        roomService.updateStatus(12L, Room.Status.CLEANING, 3L, User.Role.ADMIN);
        roomService.updateStatus(12L, Room.Status.INSPECTION, 3L, User.Role.ADMIN);
        RoomResponse response = roomService.updateStatus(
                12L,
                Room.Status.READY,
                3L,
                User.Role.ADMIN
        );

        assertThat(response.getStatus()).isEqualTo(Room.Status.READY);
    }

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void permitsEveryCanonicalTransition(Room.Status current, Room.Status requested) {
        Room room = room(12L, current, true);
        when(roomRepository.findById(12L)).thenReturn(Optional.of(room));
        when(roomRepository.save(room)).thenReturn(room);

        RoomResponse response = roomService.updateStatus(
                12L,
                requested,
                3L,
                User.Role.ADMIN
        );

        assertThat(response.getStatus()).isEqualTo(requested);
    }

    @Test
    void rejectsDirectDirtyToReadyTransition() {
        Room room = room(12L, Room.Status.DIRTY, true);
        when(roomRepository.findById(12L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.updateStatus(
                12L,
                Room.Status.READY,
                3L,
                User.Role.ADMIN
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("DIRTY to READY");

        verify(roomRepository, never()).save(any());
    }

    @Test
    void rejectsStatusChangeForInactiveRoom() {
        when(roomRepository.findById(12L))
                .thenReturn(Optional.of(room(12L, Room.Status.READY, false)));

        assertThatThrownBy(() -> roomService.updateStatus(
                12L,
                Room.Status.DIRTY,
                3L,
                User.Role.ADMIN
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Inactive rooms cannot change status.");
    }

    @Test
    void staffWithActiveEmployeeAndDepartmentCanApplyValidTransition() {
        Department department = department(true);
        Employee employee = employee(21L, department, Employee.Status.ACTIVE);
        Room room = room(12L, Room.Status.DIRTY, true);
        when(roomRepository.findById(12L)).thenReturn(Optional.of(room));
        when(employeeRepository.findByUserId(21L)).thenReturn(Optional.of(employee));
        when(roomRepository.save(room)).thenReturn(room);

        RoomResponse response = roomService.updateStatus(
                12L,
                Room.Status.CLEANING,
                21L,
                User.Role.STAFF
        );

        assertThat(response.getStatus()).isEqualTo(Room.Status.CLEANING);
    }

    @Test
    void staffWithoutOperationalEmployeeProfileIsForbidden() {
        when(roomRepository.findById(12L))
                .thenReturn(Optional.of(room(12L, Room.Status.DIRTY, true)));
        when(employeeRepository.findByUserId(21L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.updateStatus(
                12L,
                Room.Status.CLEANING,
                21L,
                User.Role.STAFF
        )).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void inactiveEmployeeOrDepartmentCannotUpdateRoomStatus() {
        Room room = room(12L, Room.Status.DIRTY, true);
        when(roomRepository.findById(12L)).thenReturn(Optional.of(room));
        when(employeeRepository.findByUserId(21L))
                .thenReturn(Optional.of(employee(
                        21L,
                        department(true),
                        Employee.Status.INACTIVE
                )));

        assertThatThrownBy(() -> roomService.updateStatus(
                12L, Room.Status.CLEANING, 21L, User.Role.STAFF
        )).isInstanceOf(ForbiddenException.class);

        when(employeeRepository.findByUserId(21L))
                .thenReturn(Optional.of(employee(
                        21L,
                        department(false),
                        Employee.Status.ACTIVE
                )));
        assertThatThrownBy(() -> roomService.updateStatus(
                12L, Room.Status.CLEANING, 21L, User.Role.STAFF
        )).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void userRoleCannotAccessRoomService() {
        RoomSearchCriteria filterCriteria1 = new RoomSearchCriteria(null, null, null, null);
        PageCriteria paginationCriteria2 = new PageCriteria(0, 20, "roomNumber,asc");
        assertThatThrownBy(() -> roomService.listRooms(filterCriteria1, paginationCriteria2, User.Role.USER)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> roomService.updateStatus(
                12L, Room.Status.DIRTY, 1L, User.Role.USER
        )).isInstanceOf(ForbiddenException.class);

        verify(roomRepository, never()).search(any(), any(), any(), any(), any());
        verify(roomRepository, never()).findById(any());
    }

    @Test
    void deactivateRejectsRoomWithNonTerminalTasks() {
        Room room = room(12L, Room.Status.READY, true);
        when(roomRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(room));
        when(taskRepository.existsByRoomIdAndStatusIn(
                12L,
                TaskService.NON_TERMINAL_STATUSES
        )).thenReturn(true);

        assertThatThrownBy(() -> roomService.deactivateRoom(12L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Room cannot be deactivated while active tasks reference it.");

        assertThat(room.getActive()).isTrue();
        verify(roomRepository, never()).save(room);
    }

    @Test
    void updateCannotBypassActiveTaskDeactivationGuard() {
        Room room = room(12L, Room.Status.READY, true);
        UpdateRoomRequest request = updateRequest();
        request.setActive(false);
        when(roomRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(room));
        when(taskRepository.existsByRoomIdAndStatusIn(
                12L,
                TaskService.NON_TERMINAL_STATUSES
        )).thenReturn(true);

        assertThatThrownBy(() -> roomService.updateRoom(12L, request))
                .isInstanceOf(ConflictException.class);

        assertThat(room.getActive()).isTrue();
    }

    private static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(Room.Status.READY, Room.Status.OCCUPIED),
                Arguments.of(Room.Status.READY, Room.Status.DIRTY),
                Arguments.of(Room.Status.READY, Room.Status.MAINTENANCE),
                Arguments.of(Room.Status.READY, Room.Status.OUT_OF_SERVICE),
                Arguments.of(Room.Status.OCCUPIED, Room.Status.DIRTY),
                Arguments.of(Room.Status.OCCUPIED, Room.Status.MAINTENANCE),
                Arguments.of(Room.Status.OCCUPIED, Room.Status.OUT_OF_SERVICE),
                Arguments.of(Room.Status.DIRTY, Room.Status.CLEANING),
                Arguments.of(Room.Status.DIRTY, Room.Status.MAINTENANCE),
                Arguments.of(Room.Status.DIRTY, Room.Status.OUT_OF_SERVICE),
                Arguments.of(Room.Status.CLEANING, Room.Status.INSPECTION),
                Arguments.of(Room.Status.CLEANING, Room.Status.MAINTENANCE),
                Arguments.of(Room.Status.CLEANING, Room.Status.OUT_OF_SERVICE),
                Arguments.of(Room.Status.INSPECTION, Room.Status.READY),
                Arguments.of(Room.Status.INSPECTION, Room.Status.CLEANING),
                Arguments.of(Room.Status.INSPECTION, Room.Status.MAINTENANCE),
                Arguments.of(Room.Status.INSPECTION, Room.Status.OUT_OF_SERVICE),
                Arguments.of(Room.Status.MAINTENANCE, Room.Status.INSPECTION),
                Arguments.of(Room.Status.MAINTENANCE, Room.Status.OUT_OF_SERVICE),
                Arguments.of(Room.Status.OUT_OF_SERVICE, Room.Status.MAINTENANCE),
                Arguments.of(Room.Status.OUT_OF_SERVICE, Room.Status.INSPECTION)
        );
    }

    private CreateRoomRequest createRequest() {
        CreateRoomRequest request = new CreateRoomRequest();
        request.setRoomNumber("218");
        request.setRoomType(" STANDARD ");
        request.setFloor(2);
        request.setInitialStatus(Room.Status.DIRTY);
        request.setNextArrivalAt(LocalDateTime.of(2030, 10, 3, 15, 0));
        return request;
    }

    private UpdateRoomRequest updateRequest() {
        UpdateRoomRequest request = new UpdateRoomRequest();
        request.setRoomType(" DELUXE ");
        request.setFloor(3);
        request.setNextArrivalAt(LocalDateTime.of(2030, 10, 4, 15, 0));
        request.setActive(true);
        return request;
    }

    private Room room(Long id, Room.Status status, boolean active) {
        Room room = new Room("218", "STANDARD", 2, status, null);
        ReflectionTestUtils.setField(room, "id", id);
        ReflectionTestUtils.setField(room, "active", active);
        return room;
    }

    private Department department(boolean active) {
        Department department = new Department("Housekeeping", null);
        ReflectionTestUtils.setField(department, "id", 3L);
        if (!active) {
            department.deactivate();
        }
        return department;
    }

    private Employee employee(
            Long userId,
            Department department,
            Employee.Status status
    ) {
        User user = new User("Worker", "worker@example.test", "bcrypt-hash");
        ReflectionTestUtils.setField(user, "id", userId);
        user.provisionEmployeeAccess(User.Role.STAFF, department);
        Employee employee = new Employee(
                "Worker",
                "worker@example.test",
                department,
                "Room Attendant",
                user
        );
        ReflectionTestUtils.setField(employee, "status", status);
        return employee;
    }
}

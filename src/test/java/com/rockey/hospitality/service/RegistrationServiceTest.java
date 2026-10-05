package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.dto.event.EventRegistrationResponse;
import com.rockey.hospitality.dto.event.EventResponse;
import com.rockey.hospitality.entity.Event;
import com.rockey.hospitality.entity.EventStatus;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.User;
import com.rockey.hospitality.entity.UserStatus;
import com.rockey.hospitality.exception.BadRequestException;
import com.rockey.hospitality.exception.ConflictException;
import com.rockey.hospitality.exception.ForbiddenException;
import com.rockey.hospitality.exception.ResourceNotFoundException;
import com.rockey.hospitality.repository.EventRepository;
import com.rockey.hospitality.repository.TaskRepository;
import com.rockey.hospitality.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TaskRepository taskRepository;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        EventService eventService = new EventService(eventRepository, taskRepository);
        registrationService = new RegistrationService(
                userRepository,
                eventRepository,
                eventService
        );
    }

    @Test
    void activeUserRegistersForOpenEventWithCapacity() {
        User user = user(31L, Role.USER, UserStatus.ACTIVE);
        Event event = event(7L, EventStatus.OPEN, 3);
        when(userRepository.findById(31L)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(event));
        when(eventRepository.countRegistrationsByEventId(7L)).thenReturn(1L);

        EventRegistrationResponse response = registrationService.register(7L, 31L);

        assertThat(response.getUserId()).isEqualTo(31L);
        assertThat(response.getEvent().getId()).isEqualTo(7L);
        assertThat(response.getEvent().getRemainingCapacity()).isEqualTo(1L);
        assertThat(user.getRegisteredEvents()).containsExactly(event);
        verify(userRepository).save(user);
    }

    @Test
    void duplicateRegistrationIsRejectedBeforeCapacityMutation() {
        User user = user(31L, Role.USER, UserStatus.ACTIVE);
        Event event = event(7L, EventStatus.OPEN, 3);
        when(userRepository.findById(31L)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(event));
        when(userRepository.existsByIdAndRegisteredEventsId(31L, 7L)).thenReturn(true);

        assertThatThrownBy(() -> registrationService.register(7L, 31L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User is already registered for this event.");
        verify(userRepository, never()).save(user);
    }

    @Test
    void fullEventIsRejected() {
        User user = user(31L, Role.USER, UserStatus.ACTIVE);
        Event event = event(7L, EventStatus.OPEN, 2);
        when(userRepository.findById(31L)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(event));
        when(eventRepository.countRegistrationsByEventId(7L)).thenReturn(2L);

        assertThatThrownBy(() -> registrationService.register(7L, 31L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Event capacity has been reached.");
    }

    @Test
    void closedCancelledAndStartedEventsRejectRegistration() {
        User user = user(31L, Role.USER, UserStatus.ACTIVE);
        when(userRepository.findById(31L)).thenReturn(Optional.of(user));

        Event closed = event(7L, EventStatus.CLOSED, 10);
        when(eventRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(closed));
        assertThatThrownBy(() -> registrationService.register(7L, 31L))
                .isInstanceOf(ConflictException.class);

        Event cancelled = event(8L, EventStatus.CANCELLED, 10);
        when(eventRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> registrationService.register(8L, 31L))
                .isInstanceOf(ConflictException.class);

        Event started = event(9L, EventStatus.OPEN, 10);
        ReflectionTestUtils.setField(
                started,
                "eventDateTime",
                LocalDateTime.now().minusMinutes(1)
        );
        when(eventRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(started));
        assertThatThrownBy(() -> registrationService.register(9L, 31L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void staffAndInactiveUserCannotSelfRegister() {
        User staff = user(21L, Role.STAFF, UserStatus.ACTIVE);
        when(userRepository.findById(21L)).thenReturn(Optional.of(staff));
        assertThatThrownBy(() -> registrationService.register(7L, 21L))
                .isInstanceOf(ForbiddenException.class);

        User inactive = user(31L, Role.USER, UserStatus.INACTIVE);
        when(userRepository.findById(31L)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> registrationService.register(7L, 31L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void registrationReportsMissingUserOrEvent() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> registrationService.register(7L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);

        User user = user(31L, Role.USER, UserStatus.ACTIVE);
        when(userRepository.findById(31L)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> registrationService.register(99L, 31L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void userWithdrawsOwnRegistrationWhileOpenAndUpcoming() {
        User user = user(31L, Role.USER, UserStatus.ACTIVE);
        Event event = event(7L, EventStatus.OPEN, 10);
        user.registerForEvent(event);
        when(userRepository.findById(31L)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(event));
        when(userRepository.existsByIdAndRegisteredEventsId(31L, 7L)).thenReturn(true);

        registrationService.withdraw(7L, 31L);

        assertThat(user.getRegisteredEvents()).isEmpty();
        verify(userRepository).save(user);
    }

    @Test
    void withdrawalRejectsMissingRegistrationAndClosedWindow() {
        User user = user(31L, Role.USER, UserStatus.ACTIVE);
        Event open = event(7L, EventStatus.OPEN, 10);
        when(userRepository.findById(31L)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(open));
        assertThatThrownBy(() -> registrationService.withdraw(7L, 31L))
                .isInstanceOf(ResourceNotFoundException.class);

        Event cancelled = event(8L, EventStatus.CANCELLED, 10);
        when(eventRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> registrationService.withdraw(8L, 31L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void listOwnRegistrationsIsPagedAndUsesCurrentUserOnly() {
        User user = user(31L, Role.USER, UserStatus.ACTIVE);
        Event event = event(7L, EventStatus.CANCELLED, 10);
        PageRequest pageRequest = PageRequest.of(
                0,
                5,
                Sort.by(Sort.Direction.DESC, "eventDateTime")
        );
        when(userRepository.findById(31L)).thenReturn(Optional.of(user));
        when(eventRepository.findRegisteredEventsByUserId(31L, pageRequest))
                .thenReturn(new PageImpl<>(List.of(event), pageRequest, 1));
        when(eventRepository.countRegistrationsByEventId(7L)).thenReturn(2L);

        PagedResponse<EventResponse> response = registrationService.listOwnRegistrations(
                31L,
                0,
                5,
                "eventDateTime,desc"
        );

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getStatus())
                .isEqualTo(EventStatus.CANCELLED);
        verify(eventRepository).findRegisteredEventsByUserId(31L, pageRequest);
    }

    @Test
    void listOwnRegistrationsRejectsInvalidPagination() {
        User user = user(31L, Role.USER, UserStatus.ACTIVE);
        when(userRepository.findById(31L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> registrationService.listOwnRegistrations(
                31L, -1, 20, "eventDateTime,asc"
        )).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> registrationService.listOwnRegistrations(
                31L, 0, 101, "eventDateTime,asc"
        )).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> registrationService.listOwnRegistrations(
                31L, 0, 20, "passwordHash,asc"
        )).isInstanceOf(BadRequestException.class);
    }

    private User user(Long id, Role role, UserStatus status) {
        User user = new User("Attendee", "attendee" + id + "@example.test", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", role);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

    private Event event(Long id, EventStatus status, int capacity) {
        Event event = new Event(
                "Leadership Conference",
                "One-day conference.",
                LocalDateTime.of(2030, 10, 10, 9, 0),
                "Ballroom A",
                capacity,
                status
        );
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}

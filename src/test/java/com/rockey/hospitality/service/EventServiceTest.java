package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.CommonDtos.PagedResponse;
import com.rockey.hospitality.dto.EventDtos.CreateEventRequest;
import com.rockey.hospitality.dto.EventDtos.EventResponse;
import com.rockey.hospitality.dto.EventDtos.UpdateEventRequest;
import com.rockey.hospitality.entity.Event;
import com.rockey.hospitality.entity.Task;
import com.rockey.hospitality.exception.ApiException.BadRequestException;
import com.rockey.hospitality.exception.ApiException.ConflictException;
import com.rockey.hospitality.exception.ApiException.ResourceNotFoundException;
import com.rockey.hospitality.repository.EventRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TaskRepository taskRepository;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, taskRepository);
    }

    @Test
    void listAppliesFiltersPaginationAndPreparationCounts() {
        Event event = event(7L, Event.Status.OPEN, futureDate(), 120);
        PageRequest pageRequest = PageRequest.of(
                1,
                5,
                Sort.by(Sort.Direction.ASC, "eventDateTime")
        );
        LocalDateTime from = LocalDateTime.of(2029, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2031, 1, 1, 0, 0);
        when(eventRepository.search(Event.Status.OPEN, from, to, pageRequest))
                .thenReturn(new PageImpl<>(List.of(event), pageRequest, 6));
        when(eventRepository.countRegistrationsByEventId(7L)).thenReturn(20L);
        when(taskRepository.countByEventId(7L)).thenReturn(5L);
        when(taskRepository.countByEventIdAndStatus(7L, Task.Status.COMPLETED))
                .thenReturn(3L);

        PagedResponse<EventResponse> response = eventService.listEvents(
                Event.Status.OPEN,
                from,
                to,
                1,
                5,
                "eventDateTime,asc"
        );

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getRegisteredCount()).isEqualTo(20);
        assertThat(response.getContent().get(0).getRemainingCapacity()).isEqualTo(100);
        assertThat(response.getContent().get(0).getTaskCount()).isEqualTo(5);
        assertThat(response.getContent().get(0).getCompletedTaskCount()).isEqualTo(3);
        assertThat(response.getTotalElements()).isEqualTo(6);
    }

    @Test
    void listRejectsInvalidRangePaginationAndSort() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime invalidStart = now.plusDays(1);
        assertThatThrownBy(() -> eventService.listEvents(
                null, invalidStart, now, 0, 20, "eventDateTime,asc"
        )).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> eventService.listEvents(
                null, null, null, -1, 20, "eventDateTime,asc"
        )).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> eventService.listEvents(
                null, null, null, 0, 101, "eventDateTime,asc"
        )).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> eventService.listEvents(
                null, null, null, 0, 20, "passwordHash,asc"
        )).isInstanceOf(BadRequestException.class);
    }

    @Test
    void createDefaultsToDraftAndNormalizesText() {
        CreateEventRequest request = createRequest();
        request.setInitialStatus(null);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", 7L);
            return event;
        });

        EventResponse response = eventService.createEvent(request);

        assertThat(response.getStatus()).isEqualTo(Event.Status.DRAFT);
        assertThat(response.getTitle()).isEqualTo("Leadership Conference");
        assertThat(response.getDescription()).isEqualTo("One-day conference.");
        assertThat(response.getLocation()).isEqualTo("Ballroom A");
        assertThat(response.getRegisteredCount()).isZero();
        assertThat(response.getTaskCount()).isZero();
    }

    @Test
    void createAllowsOpenButRejectsInvalidInitialStatusAndPastDate() {
        CreateEventRequest open = createRequest();
        open.setInitialStatus(Event.Status.OPEN);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(eventService.createEvent(open).getStatus()).isEqualTo(Event.Status.OPEN);

        CreateEventRequest invalidStatus = createRequest();
        invalidStatus.setInitialStatus(Event.Status.COMPLETED);
        assertThatThrownBy(() -> eventService.createEvent(invalidStatus))
                .isInstanceOf(ConflictException.class);

        CreateEventRequest past = createRequest();
        past.setEventDateTime(LocalDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> eventService.createEvent(past))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getReturnsCapacityAndZeroPreparationForEventWithoutTasks() {
        Event event = event(7L, Event.Status.OPEN, futureDate(), 10);
        when(eventRepository.findById(7L)).thenReturn(Optional.of(event));
        when(eventRepository.countRegistrationsByEventId(7L)).thenReturn(4L);

        EventResponse response = eventService.getEvent(7L);

        assertThat(response.getRemainingCapacity()).isEqualTo(6);
        assertThat(response.getTaskCount()).isZero();
        assertThat(response.getCompletedTaskCount()).isZero();
    }

    @Test
    void getMissingEventReturns404Exception() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEvent(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Event not found with id 99.");
    }

    @Test
    void updateAllowsCanonicalTransitionAndPreservesCapacityFloor() {
        Event event = event(7L, Event.Status.DRAFT, futureDate(), 120);
        UpdateEventRequest request = updateRequest(Event.Status.OPEN);
        request.setCapacity(80);
        when(eventRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(event));
        when(eventRepository.countRegistrationsByEventId(7L)).thenReturn(30L);
        when(eventRepository.save(event)).thenReturn(event);

        EventResponse response = eventService.updateEvent(7L, request);

        assertThat(response.getStatus()).isEqualTo(Event.Status.OPEN);
        assertThat(response.getCapacity()).isEqualTo(80);
        assertThat(response.getRemainingCapacity()).isEqualTo(50);
    }

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void updateAllowsEveryCanonicalTransition(
            Event.Status current,
            Event.Status requested
    ) {
        Event event = event(7L, current, futureDate(), 120);
        UpdateEventRequest request = updateRequest(requested);
        when(eventRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);

        assertThat(eventService.updateEvent(7L, request).getStatus())
                .isEqualTo(requested);
    }

    @Test
    void updateRejectsCapacityBelowRegistrationsAndInvalidTransition() {
        Event event = event(7L, Event.Status.OPEN, futureDate(), 120);
        UpdateEventRequest tooSmall = updateRequest(Event.Status.OPEN);
        tooSmall.setCapacity(4);
        when(eventRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(event));
        when(eventRepository.countRegistrationsByEventId(7L)).thenReturn(5L);
        assertThatThrownBy(() -> eventService.updateEvent(7L, tooSmall))
                .isInstanceOf(ConflictException.class);

        UpdateEventRequest invalidTransition = updateRequest(Event.Status.COMPLETED);
        invalidTransition.setCapacity(120);
        assertThatThrownBy(() -> eventService.updateEvent(7L, invalidTransition))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateRejectsChangedPastDateButAllowsUnchangedHistoricalDate() {
        LocalDateTime historicalDate = LocalDateTime.now().minusHours(1);
        Event event = event(7L, Event.Status.IN_PROGRESS, historicalDate, 120);
        UpdateEventRequest unchanged = updateRequest(Event.Status.IN_PROGRESS);
        unchanged.setEventDateTime(historicalDate);
        when(eventRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);

        assertThat(eventService.updateEvent(7L, unchanged).getStatus())
                .isEqualTo(Event.Status.IN_PROGRESS);

        Event second = event(8L, Event.Status.DRAFT, futureDate(), 120);
        UpdateEventRequest changedPast = updateRequest(Event.Status.DRAFT);
        changedPast.setEventDateTime(historicalDate);
        when(eventRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(second));
        assertThatThrownBy(() -> eventService.updateEvent(8L, changedPast))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cancelReturnsResponsePreservesAggregatesAndNeverDeletes() {
        Event event = event(7L, Event.Status.OPEN, futureDate(), 120);
        when(eventRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);
        when(eventRepository.countRegistrationsByEventId(7L)).thenReturn(4L);
        when(taskRepository.countByEventId(7L)).thenReturn(3L);
        when(taskRepository.countByEventIdAndStatus(7L, Task.Status.COMPLETED))
                .thenReturn(1L);

        EventResponse response = eventService.cancelEvent(7L);

        assertThat(response.getStatus()).isEqualTo(Event.Status.CANCELLED);
        assertThat(response.getRegisteredCount()).isEqualTo(4);
        assertThat(response.getTaskCount()).isEqualTo(3);
        verify(eventRepository, never()).delete(any(Event.class));
        verify(eventRepository, never()).deleteById(any(Long.class));
    }

    @Test
    void repeatedCancelAndInProgressCancelFollowCanonicalTransitions() {
        Event cancelled = event(7L, Event.Status.CANCELLED, futureDate(), 120);
        when(eventRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> eventService.cancelEvent(7L))
                .isInstanceOf(ConflictException.class);

        Event inProgress = event(8L, Event.Status.IN_PROGRESS, futureDate(), 120);
        when(eventRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(inProgress));
        assertThatThrownBy(() -> eventService.cancelEvent(8L))
                .isInstanceOf(ConflictException.class);
    }

    private CreateEventRequest createRequest() {
        CreateEventRequest request = new CreateEventRequest();
        request.setTitle("  Leadership Conference  ");
        request.setDescription("  One-day conference.  ");
        request.setEventDateTime(futureDate());
        request.setLocation("  Ballroom A  ");
        request.setCapacity(120);
        request.setInitialStatus(Event.Status.DRAFT);
        return request;
    }

    private static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(Event.Status.DRAFT, Event.Status.OPEN),
                Arguments.of(Event.Status.DRAFT, Event.Status.CANCELLED),
                Arguments.of(Event.Status.OPEN, Event.Status.CLOSED),
                Arguments.of(Event.Status.OPEN, Event.Status.IN_PROGRESS),
                Arguments.of(Event.Status.OPEN, Event.Status.CANCELLED),
                Arguments.of(Event.Status.CLOSED, Event.Status.IN_PROGRESS),
                Arguments.of(Event.Status.CLOSED, Event.Status.CANCELLED),
                Arguments.of(Event.Status.IN_PROGRESS, Event.Status.COMPLETED)
        );
    }

    private UpdateEventRequest updateRequest(Event.Status status) {
        UpdateEventRequest request = new UpdateEventRequest();
        request.setTitle("Updated Conference");
        request.setDescription("Updated details.");
        request.setEventDateTime(futureDate());
        request.setLocation("Ballroom B");
        request.setCapacity(120);
        request.setStatus(status);
        return request;
    }

    private Event event(
            Long id,
            Event.Status status,
            LocalDateTime eventDateTime,
            int capacity
    ) {
        Event event = new Event(
                "Leadership Conference",
                "One-day conference.",
                eventDateTime,
                "Ballroom A",
                capacity,
                status
        );
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    private LocalDateTime futureDate() {
        return LocalDateTime.of(2030, 10, 10, 9, 0);
    }
}

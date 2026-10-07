package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.dto.event.CreateEventRequest;
import com.rockey.hospitality.dto.event.EventResponse;
import com.rockey.hospitality.dto.event.EventSummary;
import com.rockey.hospitality.dto.event.UpdateEventRequest;
import com.rockey.hospitality.entity.Event;
import com.rockey.hospitality.entity.EventStatus;
import com.rockey.hospitality.entity.TaskStatus;
import com.rockey.hospitality.exception.BadRequestException;
import com.rockey.hospitality.exception.ConflictException;
import com.rockey.hospitality.exception.ResourceNotFoundException;
import com.rockey.hospitality.repository.EventRepository;
import com.rockey.hospitality.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class EventService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "title",
            "eventDateTime",
            "location",
            "capacity",
            "status",
            "createdAt",
            "updatedAt"
    );
    private static final Set<EventStatus> TERMINAL_STATUSES = Set.of(
            EventStatus.COMPLETED,
            EventStatus.CANCELLED
    );
    private static final Map<EventStatus, Set<EventStatus>> ALLOWED_TRANSITIONS = Map.of(
            EventStatus.DRAFT,
            Set.of(EventStatus.OPEN, EventStatus.CANCELLED),
            EventStatus.OPEN,
            Set.of(EventStatus.CLOSED, EventStatus.IN_PROGRESS, EventStatus.CANCELLED),
            EventStatus.CLOSED,
            Set.of(EventStatus.IN_PROGRESS, EventStatus.CANCELLED),
            EventStatus.IN_PROGRESS,
            Set.of(EventStatus.COMPLETED),
            EventStatus.COMPLETED,
            Set.of(),
            EventStatus.CANCELLED,
            Set.of()
    );

    private final EventRepository eventRepository;
    private final TaskRepository taskRepository;

    public EventService(EventRepository eventRepository, TaskRepository taskRepository) {
        this.eventRepository = eventRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<EventResponse> listEvents(
            EventStatus status,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            int page,
            int size,
            String sort
    ) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BadRequestException("Event date range is invalid.");
        }
        Page<Event> events = eventRepository.search(
                status,
                dateFrom,
                dateTo,
                pageRequest(page, size, sort)
        );
        return new PagedResponse<>(
                events.getContent().stream().map(this::toResponse).toList(),
                events.getNumber(),
                events.getSize(),
                events.getTotalElements(),
                events.getTotalPages(),
                events.isLast()
        );
    }

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        validateFutureEventDate(request.getEventDateTime(), LocalDateTime.now());
        EventStatus initialStatus = request.getInitialStatus() == null
                ? EventStatus.DRAFT
                : request.getInitialStatus();
        if (initialStatus != EventStatus.DRAFT && initialStatus != EventStatus.OPEN) {
            throw new ConflictException("New events must begin in DRAFT or OPEN status.");
        }
        Event event = new Event(
                request.getTitle().trim(),
                normalizeDescription(request.getDescription()),
                request.getEventDateTime(),
                request.getLocation().trim(),
                request.getCapacity(),
                initialStatus
        );
        return toResponse(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long eventId) {
        return toResponse(findEvent(eventId));
    }

    @Transactional
    public EventResponse updateEvent(Long eventId, UpdateEventRequest request) {
        Event event = findEventForUpdate(eventId);
        ensureNotTerminal(event);
        LocalDateTime now = LocalDateTime.now();
        if (!Objects.equals(event.getEventDateTime(), request.getEventDateTime())) {
            validateFutureEventDate(request.getEventDateTime(), now);
        }

        long registeredCount = eventRepository.countRegistrationsByEventId(eventId);
        if (request.getCapacity() < registeredCount) {
            throw new ConflictException(
                    "Event capacity cannot be lower than its registration count."
            );
        }

        EventStatus requestedStatus = request.getStatus();
        if (event.getStatus() != requestedStatus) {
            ensureTransitionAllowed(event.getStatus(), requestedStatus);
        }
        if (requestedStatus == EventStatus.OPEN
                && !request.getEventDateTime().isAfter(now)) {
            throw new ConflictException("OPEN events must be scheduled in the future.");
        }

        event.update(
                request.getTitle().trim(),
                normalizeDescription(request.getDescription()),
                request.getEventDateTime(),
                request.getLocation().trim(),
                request.getCapacity(),
                requestedStatus
        );
        return toResponse(eventRepository.save(event));
    }

    @Transactional
    public EventResponse cancelEvent(Long eventId) {
        // Cancellation keeps registration and preparation-Task history instead of deleting rows.
        Event event = findEventForUpdate(eventId);
        ensureNotTerminal(event);
        ensureTransitionAllowed(event.getStatus(), EventStatus.CANCELLED);
        event.cancel();
        return toResponse(eventRepository.save(event));
    }

    EventResponse toResponse(Event event) {
        long registeredCount = eventRepository.countRegistrationsByEventId(event.getId());
        long taskCount = taskRepository.countByEventId(event.getId());
        long completedTaskCount = taskRepository.countByEventIdAndStatus(
                event.getId(),
                TaskStatus.COMPLETED
        );
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getEventDateTime(),
                event.getLocation(),
                event.getCapacity(),
                event.getStatus(),
                registeredCount,
                Math.max(0L, event.getCapacity() - registeredCount),
                taskCount,
                completedTaskCount,
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }

    EventSummary toSummary(Event event, long registeredCount) {
        return new EventSummary(
                event.getId(),
                event.getTitle(),
                event.getEventDateTime(),
                event.getLocation(),
                event.getStatus(),
                Math.max(0L, event.getCapacity() - registeredCount)
        );
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found with id " + eventId + "."
                ));
    }

    private Event findEventForUpdate(Long eventId) {
        return eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found with id " + eventId + "."
                ));
    }

    private void ensureNotTerminal(Event event) {
        if (TERMINAL_STATUSES.contains(event.getStatus())) {
            throw new ConflictException("Completed or cancelled events are terminal.");
        }
    }

    private void ensureTransitionAllowed(EventStatus current, EventStatus requested) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(requested)) {
            throw new ConflictException(
                    "Event transition from " + current + " to " + requested
                            + " is not allowed."
            );
        }
    }

    private void validateFutureEventDate(LocalDateTime eventDateTime, LocalDateTime now) {
        if (eventDateTime == null || !eventDateTime.isAfter(now)) {
            throw new BadRequestException("Event date and time must be in the future.");
        }
    }

    private PageRequest pageRequest(int page, int size, String sortValue) {
        if (page < 0) {
            throw new BadRequestException("Page must be zero or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size must be between 1 and 100.");
        }
        String[] sortParts = sortValue == null || sortValue.isBlank()
                ? new String[]{"eventDateTime", "asc"}
                : sortValue.split(",", -1);
        if (sortParts.length > 2 || !ALLOWED_SORT_FIELDS.contains(sortParts[0])) {
            throw new BadRequestException("Event sort is invalid.");
        }
        Sort.Direction direction;
        try {
            direction = sortParts.length == 1
                    ? Sort.Direction.ASC
                    : Sort.Direction.fromString(sortParts[1]);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Event sort direction is invalid.");
        }
        return PageRequest.of(page, size, Sort.by(direction, sortParts[0]));
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}

package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.CommonDtos.PagedResponse;
import com.rockey.hospitality.dto.EventDtos.CreateEventRequest;
import com.rockey.hospitality.dto.EventDtos.EventResponse;
import com.rockey.hospitality.dto.EventDtos.EventSummary;
import com.rockey.hospitality.dto.EventDtos.UpdateEventRequest;
import com.rockey.hospitality.entity.Event;
import com.rockey.hospitality.entity.Task;
import com.rockey.hospitality.exception.ApiException.BadRequestException;
import com.rockey.hospitality.exception.ApiException.ConflictException;
import com.rockey.hospitality.exception.ApiException.ResourceNotFoundException;
import com.rockey.hospitality.repository.EventRepository;
import com.rockey.hospitality.repository.TaskRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STUDY NOTE: A Service holds business rules and coordinates an application workflow.
 * Here, @Service lets Spring manage and inject this component; @Transactional groups database work so unchecked
 * failures roll back writes.
 * EventService enforces Event lifecycle, preserves cancellation history and supplies
 * registration/preparation counts.
 * EventController delegates here; Event and Task repositories provide the persisted data through
 * JPA/Hibernate.
 */
@Service
public class EventService {

    // Transaction study key: Spring applies @Transactional when another component calls this managed service.
    // readOnly=true requests a read-oriented transaction; it keeps lazy reads and DTO mapping inside the
    // persistence boundary.
    // readOnly is not an authorization rule; repositories still run only after the service's scope checks.

    /**
     * Maximum of 100 rows per requested page, shared by this service's pagination checks.
     */
    private static final int MAX_PAGE_SIZE = 100;
    /**
     * Allowlist of sortable persisted fields, rejecting arbitrary property paths from request input.
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "title",
            "eventDateTime",
            "location",
            "capacity",
            "status",
            "createdAt",
            "updatedAt"
    );
    /**
     * COMPLETED and CANCELLED Event states are terminal and cannot be edited.
     */
    private static final Set<Event.Status> TERMINAL_STATUSES = Set.of(
            Event.Status.COMPLETED,
            Event.Status.CANCELLED
    );
    /**
     * Explicit allowed next-status map used to reject skipped or terminal lifecycle changes.
     */
    private static final Map<Event.Status, Set<Event.Status>> ALLOWED_TRANSITIONS = Map.of(
            Event.Status.DRAFT,
            Set.of(Event.Status.OPEN, Event.Status.CANCELLED),
            Event.Status.OPEN,
            Set.of(Event.Status.CLOSED, Event.Status.IN_PROGRESS, Event.Status.CANCELLED),
            Event.Status.CLOSED,
            Set.of(Event.Status.IN_PROGRESS, Event.Status.CANCELLED),
            Event.Status.IN_PROGRESS,
            Set.of(Event.Status.COMPLETED),
            Event.Status.COMPLETED,
            Set.of(),
            Event.Status.CANCELLED,
            Set.of()
    );

    /**
     * Injected EventRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final EventRepository eventRepository;
    /**
     * Injected TaskRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final TaskRepository taskRepository;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public EventService(EventRepository eventRepository, TaskRepository taskRepository) {
        this.eventRepository = eventRepository;
        this.taskRepository = taskRepository;
    }

    /**
     * Validates the date range and pages Events using optional status and inclusive date bounds.
     * Each response includes persisted registration and preparation counts.
     */
    @Transactional(readOnly = true)
    public PagedResponse<EventResponse> listEvents(
            Event.Status status,
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

    /**
     * Requires a future date and an initial DRAFT or OPEN status before saving the normalized Event.
     */
    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        validateFutureEventDate(request.getEventDateTime(), LocalDateTime.now());
        Event.Status initialStatus = request.getInitialStatus() == null
                ? Event.Status.DRAFT
                : request.getInitialStatus();
        if (initialStatus != Event.Status.DRAFT && initialStatus != Event.Status.OPEN) {
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

    /**
     * Loads one Event and assembles its safe details and aggregate counts.
     */
    @Transactional(readOnly = true)
    public EventResponse getEvent(Long eventId) {
        return toResponse(findEvent(eventId));
    }

    /**
     * Locks a non-terminal Event, protects registered capacity, and validates changed dates and status transitions before updating details.
     */
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

        Event.Status requestedStatus = request.getStatus();
        if (event.getStatus() != requestedStatus) {
            ensureTransitionAllowed(event.getStatus(), requestedStatus);
        }
        if (requestedStatus == Event.Status.OPEN
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

    /**
     * Locks an eligible Event and transitions it to CANCELLED.
     * Registrations, linked Tasks, and the Event row remain intact.
     */
    @Transactional
    public EventResponse cancelEvent(Long eventId) {
        // Cancellation keeps registration and preparation-Task history instead of deleting rows.
        Event event = findEventForUpdate(eventId);
        ensureNotTerminal(event);
        ensureTransitionAllowed(event.getStatus(), Event.Status.CANCELLED);
        event.cancel();
        return toResponse(eventRepository.save(event));
    }

    /**
     * Builds Event details with retained registration count, nonnegative remaining capacity, total preparation Tasks, and completed preparation Tasks.
     */
    EventResponse toResponse(Event event) {
        long registeredCount = eventRepository.countRegistrationsByEventId(event.getId());
        long taskCount = taskRepository.countByEventId(event.getId());
        long completedTaskCount = taskRepository.countByEventIdAndStatus(
                event.getId(),
                Task.Status.COMPLETED
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

    /**
     * Builds a compact registration response using a supplied registration count to calculate remaining capacity.
     */
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

    /**
     * Centralizes Event lookup and its missing-resource error.
     */
    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found with id " + eventId + "."
                ));
    }

    /**
     * Loads an Event with a write lock shared by lifecycle and capacity-sensitive registration operations.
     */
    private Event findEventForUpdate(Long eventId) {
        return eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found with id " + eventId + "."
                ));
    }

    /**
     * Prevents editing or cancelling an already completed or cancelled Event.
     */
    private void ensureNotTerminal(Event event) {
        if (TERMINAL_STATUSES.contains(event.getStatus())) {
            throw new ConflictException("Completed or cancelled events are terminal.");
        }
    }

    /**
     * Checks the explicit Event transition map rather than accepting arbitrary status changes.
     */
    private void ensureTransitionAllowed(Event.Status current, Event.Status requested) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(requested)) {
            throw new ConflictException(
                    "Event transition from " + current + " to " + requested
                            + " is not allowed."
            );
        }
    }

    /**
     * Requires an Event timestamp strictly after the supplied current server time.
     */
    private void validateFutureEventDate(LocalDateTime eventDateTime, LocalDateTime now) {
        if (eventDateTime == null || !eventDateTime.isAfter(now)) {
            throw new BadRequestException("Event date and time must be in the future.");
        }
    }

    /**
     * Validates zero-based page, size 1–100, and an allowlisted sort field and direction.
     * Omitted sorting uses eventDateTime ascending, preventing arbitrary property paths.
     */
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

    /**
     * Stores blank descriptions as null and trims nonblank text.
     */
    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}

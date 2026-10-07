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
import com.rockey.hospitality.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Manages an active USER's own Event registrations through the User join-table relationship.
 * Event row locking serializes capacity-sensitive changes.
 */
// Registers this business/security service for constructor injection.
@Service
public class RegistrationService {

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
            "status"
    );

    /**
     * Injected UserRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final UserRepository userRepository;
    /**
     * Injected EventRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final EventRepository eventRepository;
    /**
     * Injected EventService collaborator; this layer delegates the operation rather than duplicating its rules.
     */
    private final EventService eventService;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public RegistrationService(
            UserRepository userRepository,
            EventRepository eventRepository,
            EventService eventService
    ) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.eventService = eventService;
    }

    // Capacity/duplicate reads after the Event lock must see the preceding commit,
    // not an InnoDB REPEATABLE READ snapshot established by the attendee lookup.
    /**
     * Locks an upcoming OPEN Event, checks duplicate membership and remaining capacity, then adds it to the active USER's registrations.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes. READ_COMMITTED lets checks after a lock observe preceding committed changes.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EventRegistrationResponse register(Long eventId, Long userId) {
        User user = findActiveAttendee(userId);
        // Serialize registrations on the Event so capacity and duplicate checks agree before commit.
        Event event = findEventForUpdate(eventId);
        if (event.getStatus() != EventStatus.OPEN
                || !event.getEventDateTime().isAfter(LocalDateTime.now())) {
            throw new ConflictException("Registration is available only for upcoming OPEN events.");
        }
        if (userRepository.existsByIdAndRegisteredEventsId(userId, eventId)) {
            throw new ConflictException("User is already registered for this event.");
        }
        long registeredCount = eventRepository.countRegistrationsByEventId(eventId);
        if (registeredCount >= event.getCapacity()) {
            throw new ConflictException("Event capacity has been reached.");
        }

        user.registerForEvent(event);
        userRepository.save(user);
        return new EventRegistrationResponse(
                user.getId(),
                eventService.toSummary(event, registeredCount + 1)
        );
    }

    /**
     * Locks an upcoming OPEN Event and removes the caller's existing membership.
     * Missing registration or closed eligibility is reported instead of deleting the Event.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes. READ_COMMITTED lets checks after a lock observe preceding committed changes.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void withdraw(Long eventId, Long userId) {
        User user = findActiveAttendee(userId);
        Event event = findEventForUpdate(eventId);
        if (event.getStatus() != EventStatus.OPEN
                || !event.getEventDateTime().isAfter(LocalDateTime.now())) {
            throw new ConflictException(
                    "Registration withdrawal is closed for this event."
            );
        }
        if (!userRepository.existsByIdAndRegisteredEventsId(userId, eventId)) {
            throw new ResourceNotFoundException("Event registration was not found.");
        }

        user.withdrawFromEvent(event);
        userRepository.save(user);
    }

    /**
     * Pages only the active USER's retained Event memberships and maps them to the shared Event response shape.
     */
    // Runs this service operation in a read-only transaction, keeping lazy reads and DTO mapping inside the persistence boundary.
    @Transactional(readOnly = true)
    public PagedResponse<EventResponse> listOwnRegistrations(
            Long userId,
            int page,
            int size,
            String sort
    ) {
        findActiveAttendee(userId);
        Page<Event> events = eventRepository.findRegisteredEventsByUserId(
                userId,
                pageRequest(page, size, sort)
        );
        return new PagedResponse<>(
                events.getContent().stream().map(eventService::toResponse).toList(),
                events.getNumber(),
                events.getSize(),
                events.getTotalElements(),
                events.getTotalPages(),
                events.isLast()
        );
    }

    /**
     * Requires an existing active USER account for self-registration; STAFF and ADMIN cannot use this attendee flow.
     */
    private User findActiveAttendee(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id " + userId + "."
                ));
        if (user.getRole() != Role.USER || user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException(
                    "Event self-registration requires an active USER account."
            );
        }
        return user;
    }

    /**
     * Acquires the Event write lock used to serialize registration, capacity, and lifecycle changes.
     */
    private Event findEventForUpdate(Long eventId) {
        return eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found with id " + eventId + "."
                ));
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
            throw new BadRequestException("Event registration sort is invalid.");
        }
        Sort.Direction direction;
        try {
            direction = sortParts.length == 1
                    ? Sort.Direction.ASC
                    : Sort.Direction.fromString(sortParts[1]);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Event registration sort direction is invalid.");
        }
        return PageRequest.of(page, size, Sort.by(direction, sortParts[0]));
    }
}

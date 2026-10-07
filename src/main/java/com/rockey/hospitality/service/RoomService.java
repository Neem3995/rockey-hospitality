package com.rockey.hospitality.service;

import com.rockey.hospitality.dto.common.PagedResponse;
import com.rockey.hospitality.dto.common.PageCriteria;
import com.rockey.hospitality.dto.room.RoomSearchCriteria;
import com.rockey.hospitality.dto.room.CreateRoomRequest;
import com.rockey.hospitality.dto.room.RoomResponse;
import com.rockey.hospitality.dto.room.UpdateRoomRequest;
import com.rockey.hospitality.entity.Employee;
import com.rockey.hospitality.entity.EmployeeStatus;
import com.rockey.hospitality.entity.Role;
import com.rockey.hospitality.entity.Room;
import com.rockey.hospitality.entity.RoomStatus;
import com.rockey.hospitality.exception.BadRequestException;
import com.rockey.hospitality.exception.ConflictException;
import com.rockey.hospitality.exception.ForbiddenException;
import com.rockey.hospitality.exception.ResourceNotFoundException;
import com.rockey.hospitality.repository.EmployeeRepository;
import com.rockey.hospitality.repository.RoomRepository;
import com.rockey.hospitality.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Manages Room details, soft lifecycle, and permitted turnover transitions.
 * Service checks enforce STAFF eligibility and prevent deactivation while active Tasks reference the Room.
 */
// Registers this business/security service for constructor injection.
@Service
public class RoomService {

    /**
     * Maximum of 100 rows per requested page, shared by this service's pagination checks.
     */
    private static final int MAX_PAGE_SIZE = 100;
    /**
     * Allowlist of sortable persisted fields, rejecting arbitrary property paths from request input.
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "roomNumber",
            "roomType",
            "floor",
            "status",
            "nextArrivalAt",
            "createdAt"
    );
    // Turnover is a lifecycle: clients cannot skip checks by choosing an arbitrary status.
    /**
     * Explicit allowed next-status map used to reject skipped or terminal lifecycle changes.
     */
    private static final Map<RoomStatus, Set<RoomStatus>> ALLOWED_TRANSITIONS = Map.of(
            RoomStatus.READY,
            Set.of(
                    RoomStatus.OCCUPIED,
                    RoomStatus.DIRTY,
                    RoomStatus.MAINTENANCE,
                    RoomStatus.OUT_OF_SERVICE
            ),
            RoomStatus.OCCUPIED,
            Set.of(
                    RoomStatus.DIRTY,
                    RoomStatus.MAINTENANCE,
                    RoomStatus.OUT_OF_SERVICE
            ),
            RoomStatus.DIRTY,
            Set.of(
                    RoomStatus.CLEANING,
                    RoomStatus.MAINTENANCE,
                    RoomStatus.OUT_OF_SERVICE
            ),
            RoomStatus.CLEANING,
            Set.of(
                    RoomStatus.INSPECTION,
                    RoomStatus.MAINTENANCE,
                    RoomStatus.OUT_OF_SERVICE
            ),
            RoomStatus.INSPECTION,
            Set.of(
                    RoomStatus.READY,
                    RoomStatus.CLEANING,
                    RoomStatus.MAINTENANCE,
                    RoomStatus.OUT_OF_SERVICE
            ),
            RoomStatus.MAINTENANCE,
            Set.of(RoomStatus.INSPECTION, RoomStatus.OUT_OF_SERVICE),
            RoomStatus.OUT_OF_SERVICE,
            Set.of(RoomStatus.MAINTENANCE, RoomStatus.INSPECTION)
    );

    /**
     * Injected RoomRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final RoomRepository roomRepository;
    /**
     * Injected EmployeeRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final EmployeeRepository employeeRepository;
    /**
     * Injected TaskRepository for database lookup and persistence, keeping SQL access out of controller code.
     */
    private final TaskRepository taskRepository;

    /**
     * Receives the collaborating components through constructor injection, making dependencies explicit and replaceable in tests.
     */
    public RoomService(
            RoomRepository roomRepository,
            EmployeeRepository employeeRepository,
            TaskRepository taskRepository
    ) {
        this.roomRepository = roomRepository;
        this.employeeRepository = employeeRepository;
        this.taskRepository = taskRepository;
    }

    /**
     * Validates filters and pages Room DTOs.
     * STAFF is forced to active Rooms and cannot request inactive history.
     */
    // Runs this service operation in a read-only transaction, keeping lazy reads and DTO mapping inside the persistence boundary.
    @Transactional(readOnly = true)
    public PagedResponse<RoomResponse> listRooms(RoomSearchCriteria criteria, PageCriteria pagination, Role requesterRole) {
        RoomStatus status = criteria.status();
        Integer floor = criteria.floor();
        String roomType = criteria.roomType();
        Boolean active = criteria.active();
        ensureRoomViewer(requesterRole);
        if (floor != null && (floor < 1 || floor > 99)) {
            throw new BadRequestException("Floor filter must be between 1 and 99.");
        }
        String normalizedType = normalizeOptionalRoomType(roomType);
        Boolean effectiveActive = active;
        if (requesterRole == Role.STAFF) {
            if (Boolean.FALSE.equals(active)) {
                throw new ForbiddenException("STAFF may view only active rooms.");
            }
            effectiveActive = true;
        }

        Page<Room> rooms = roomRepository.search(
                status,
                floor,
                normalizedType,
                effectiveActive,
                pageRequest(pagination.page(), pagination.size(), pagination.sort())
        );
        return new PagedResponse<>(
                rooms.getContent().stream().map(this::toResponse).toList(),
                rooms.getNumber(),
                rooms.getSize(),
                rooms.getTotalElements(),
                rooms.getTotalPages(),
                rooms.isLast()
        );
    }

    /**
     * Checks readiness time and normalized Room-number uniqueness before saving Room details with its initial lifecycle status.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes.
    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        validateNextArrival(request.getNextArrivalAt());
        String roomNumber = normalizeRoomNumber(request.getRoomNumber());
        if (roomRepository.existsByRoomNumberIgnoreCase(roomNumber)) {
            throw new ConflictException("Room number is already registered.");
        }

        Room room = new Room(
                roomNumber,
                request.getRoomType().trim(),
                request.getFloor(),
                request.getInitialStatus(),
                request.getNextArrivalAt()
        );
        return toResponse(roomRepository.save(room));
    }

    /**
     * Returns one Room DTO to STAFF or ADMIN, blocking STAFF access to inactive Rooms.
     */
    // Runs this service operation in a read-only transaction, keeping lazy reads and DTO mapping inside the persistence boundary.
    @Transactional(readOnly = true)
    public RoomResponse getRoom(Long roomId, Role requesterRole) {
        ensureRoomViewer(requesterRole);
        Room room = findRoom(roomId);
        if (requesterRole == Role.STAFF && !Boolean.TRUE.equals(room.getActive())) {
            throw new ForbiddenException("STAFF may view only active rooms.");
        }
        return toResponse(room);
    }

    /**
     * Locks the Room, checks its arrival time, and blocks deactivation if non-terminal Tasks reference it.
     * Status transitions use their separate operation.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes.
    @Transactional
    public RoomResponse updateRoom(Long roomId, UpdateRoomRequest request) {
        validateNextArrival(request.getNextArrivalAt());
        Room room = findRoomForUpdate(roomId);
        if (Boolean.FALSE.equals(request.getActive())) {
            ensureNoActiveTasks(roomId);
        }
        room.updateDetails(
                request.getRoomType().trim(),
                request.getFloor(),
                request.getNextArrivalAt(),
                request.getActive()
        );
        return toResponse(roomRepository.save(room));
    }

    /**
     * Locks the Room and checks active work before setting active=false, preserving the row and references.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes.
    @Transactional
    public void deactivateRoom(Long roomId) {
        Room room = findRoomForUpdate(roomId);
        ensureNoActiveTasks(roomId);
        room.deactivate();
        roomRepository.save(room);
    }

    /**
     * Blocks Room deactivation while OPEN, ASSIGNED, or IN_PROGRESS Tasks reference it.
     */
    private void ensureNoActiveTasks(Long roomId) {
        if (taskRepository.existsByRoomIdAndStatusIn(
                roomId,
                TaskService.NON_TERMINAL_STATUSES
        )) {
            throw new ConflictException(
                    "Room cannot be deactivated while active tasks reference it."
            );
        }
    }

    /**
     * Requires an active Room and an allowed turnover transition.
     * STAFF also needs an active Employee in an active Department.
     */
    // Starts or joins a transaction for calls through Spring; unchecked failures roll back its writes.
    @Transactional
    public RoomResponse updateStatus(
            Long roomId,
            RoomStatus requestedStatus,
            Long requesterUserId,
            Role requesterRole
    ) {
        if (requesterRole != Role.STAFF && requesterRole != Role.ADMIN) {
            throw new ForbiddenException("Room status access is forbidden.");
        }
        Room room = findRoom(roomId);
        if (!Boolean.TRUE.equals(room.getActive())) {
            throw new ConflictException("Inactive rooms cannot change status.");
        }
        if (requesterRole == Role.STAFF) {
            ensureEligibleStaff(requesterUserId);
        }
        if (!ALLOWED_TRANSITIONS
                .getOrDefault(room.getStatus(), Set.of())
                .contains(requestedStatus)) {
            throw new ConflictException(
                    "Room transition from " + room.getStatus()
                            + " to " + requestedStatus + " is not allowed."
            );
        }

        room.updateStatus(requestedStatus);
        return toResponse(roomRepository.save(room));
    }

    /**
     * Requires a linked active operational Employee and active Department rather than trusting the STAFF role alone.
     */
    private void ensureEligibleStaff(Long userId) {
        // Room changes require an active Employee in an active Department, not just a STAFF label.
        Employee employee = employeeRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException(
                        "STAFF must have an active operational employee profile."
                ));
        if (employee.getStatus() != EmployeeStatus.ACTIVE
                || !Boolean.TRUE.equals(employee.getDepartment().getActive())) {
            throw new ForbiddenException(
                    "STAFF must have an active operational employee profile."
            );
        }
    }

    /**
     * Rejects Room reads by roles other than STAFF and ADMIN.
     */
    private void ensureRoomViewer(Role requesterRole) {
        if (requesterRole != Role.STAFF && requesterRole != Role.ADMIN) {
            throw new ForbiddenException("Room access is forbidden.");
        }
    }

    /**
     * Loads a Room by ID or raises the common missing-resource error.
     */
    private Room findRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Room not found with id " + roomId + "."
                ));
    }

    /**
     * Acquires the Room write lock used by deactivation and new Task reference checks.
     */
    private Room findRoomForUpdate(Long roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Room not found with id " + roomId + "."
                ));
    }

    /**
     * Validates zero-based page, size 1–100, and an allowlisted sort field and direction.
     * Omitted sorting uses roomNumber ascending, preventing arbitrary property paths.
     */
    private PageRequest pageRequest(int page, int size, String sortValue) {
        if (page < 0) {
            throw new BadRequestException("Page must be zero or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size must be between 1 and 100.");
        }
        String[] sortParts = sortValue == null || sortValue.isBlank()
                ? new String[]{"roomNumber", "asc"}
                : sortValue.split(",", -1);
        if (sortParts.length > 2 || !ALLOWED_SORT_FIELDS.contains(sortParts[0])) {
            throw new BadRequestException("Room sort is invalid.");
        }
        Sort.Direction direction;
        try {
            direction = sortParts.length == 1
                    ? Sort.Direction.ASC
                    : Sort.Direction.fromString(sortParts[1]);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Room sort direction is invalid.");
        }
        return PageRequest.of(page, size, Sort.by(direction, sortParts[0]));
    }

    /**
     * Allows no arrival value or a current/future server-local timestamp.
     * This is readiness data, not a booking record.
     */
    private void validateNextArrival(LocalDateTime nextArrivalAt) {
        if (nextArrivalAt != null && nextArrivalAt.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Next arrival time must be current or future.");
        }
    }

    /**
     * Trims and uppercases Room numbers, allowing 1–10 letters, digits, or hyphens.
     */
    private String normalizeRoomNumber(String roomNumber) {
        String normalized = roomNumber.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9-]{1,10}")) {
            throw new BadRequestException(
                    "Room number must contain 1-10 letters, numbers, or hyphens."
            );
        }
        return normalized;
    }

    /**
     * Preserves an omitted type filter or trims and validates its 2–50 character value.
     */
    private String normalizeOptionalRoomType(String roomType) {
        if (roomType == null) {
            return null;
        }
        String normalized = roomType.trim();
        if (normalized.length() < 2 || normalized.length() > 50) {
            throw new BadRequestException("Room type filter must be between 2 and 50 characters.");
        }
        return normalized;
    }

    /**
     * Maps Room fields to the public DTO without exposing JPA persistence details.
     */
    private RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getRoomNumber(),
                room.getRoomType(),
                room.getStatus(),
                room.getFloor(),
                room.getNextArrivalAt(),
                room.getActive(),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }
}

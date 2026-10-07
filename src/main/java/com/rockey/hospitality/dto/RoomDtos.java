package com.rockey.hospitality.dto;

import com.rockey.hospitality.entity.Room;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * STUDY NOTE: DTO means Data Transfer Object: a plain Java type that carries request, response or internal
 * input data.
 * This container groups Room requests, responses and internal search inputs for operational readiness, not
 * bookings.
 * Controllers and services use these types to separate the API contract from JPA entities and avoid
 * exposing database objects directly.
 * Static nested types need no container instance; validation/JSON annotations describe data, not access
 * permissions.
 */
public final class RoomDtos {

    // A record is a compact Java type with fixed named values; these criteria carry internal filters/paging,
    // not new public API operations.
    // Validation study key (the numbers/patterns are specified on each annotated field):
    // @NotBlank requires non-null text containing at least one non-whitespace character.
    // @NotNull requires a value; it does not check text length or a numeric range.
    // @Size checks length/count against the declared min/max (text length for the fields here).
    // @Min sets an inclusive minimum for a supplied number.
    // @Max sets an inclusive maximum for a supplied number.
    // @FutureOrPresent allows a supplied date/time at the validator's current time or later.
    // Most shape/range validators accept null; @NotNull or @NotBlank supplies required-value checks.

    // Namespace only; callers construct the nested types instead.
    private RoomDtos() { }

    /**
     * Writable create room JSON DTO, separate from the entity and returned fields.
     * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
     */
    public static class CreateRoomRequest {

        /**
         * Unique operational Room identifier; it is not a database ID or a booking.
         */
        @NotBlank(message = "Room number is required.")
        private String roomNumber;

        /**
         * Operational Room classification used in display and type filtering.
         */
        @NotBlank(message = "Room type is required.")
        @Size(min = 2, max = 50, message = "Room type must be between 2 and 50 characters.")
        private String roomType;

        /**
         * Room floor or optional floor filter; service validation constrains supplied values.
         */
        @NotNull(message = "Floor is required.")
        @Min(value = 1, message = "Floor must be between 1 and 99.")
        @Max(value = 99, message = "Floor must be between 1 and 99.")
        private Integer floor;

        /**
         * Optional starting lifecycle status; the service/entity applies its documented default and eligibility rules.
         */
        private Room.Status initialStatus;

        /**
         * Optional server-local arrival readiness time, not a Reservation or Booking relationship.
         */
        // Allows a supplied timestamp at the current time or in the future.
        @FutureOrPresent(message = "Next arrival time must be current or future.")
        private LocalDateTime nextArrivalAt;

        public String getRoomNumber() {
            return roomNumber;
        }

        public void setRoomNumber(String roomNumber) {
            this.roomNumber = roomNumber;
        }

        public String getRoomType() {
            return roomType;
        }

        public void setRoomType(String roomType) {
            this.roomType = roomType;
        }

        public Integer getFloor() {
            return floor;
        }

        public void setFloor(Integer floor) {
            this.floor = floor;
        }

        public Room.Status getInitialStatus() {
            return initialStatus;
        }

        public void setInitialStatus(Room.Status initialStatus) {
            this.initialStatus = initialStatus;
        }

        public LocalDateTime getNextArrivalAt() {
            return nextArrivalAt;
        }

        public void setNextArrivalAt(LocalDateTime nextArrivalAt) {
            this.nextArrivalAt = nextArrivalAt;
        }
    }

    /**
     * Safe room response DTO built from validated service results instead of serializing the entity.
     */
    public static class RoomResponse {

        /**
         * Database identifier used to refer to this resource in requests and relationships.
         */
        private final Long id;
        /**
         * Unique operational Room identifier; it is not a database ID or a booking.
         */
        private final String roomNumber;
        /**
         * Operational Room classification used in display and type filtering.
         */
        private final String roomType;
        /**
         * Lifecycle enum value interpreted by this resource's service and transition rules.
         */
        private final Room.Status status;
        /**
         * Room floor or optional floor filter; service validation constrains supplied values.
         */
        private final Integer floor;
        /**
         * Optional server-local arrival readiness time, not a Reservation or Booking relationship.
         */
        private final LocalDateTime nextArrivalAt;
        /**
         * Soft-lifecycle flag; inactive rows retain their identity and history.
         */
        private final Boolean active;
        /**
         * Server-local creation timestamp retained for history.
         */
        private final LocalDateTime createdAt;
        /**
         * Server-local timestamp of the latest persisted entity update.
         */
        private final LocalDateTime updatedAt;

        /**
         * Packages the listed response fields supplied by the service without serializing a persistence entity.
         */
        public RoomResponse(
                Long id,
                String roomNumber,
                String roomType,
                Room.Status status,
                Integer floor,
                LocalDateTime nextArrivalAt,
                Boolean active,
                LocalDateTime createdAt,
                LocalDateTime updatedAt
        ) {
            this.id = id;
            this.roomNumber = roomNumber;
            this.roomType = roomType;
            this.status = status;
            this.floor = floor;
            this.nextArrivalAt = nextArrivalAt;
            this.active = active;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public Long getId() {
            return id;
        }

        public String getRoomNumber() {
            return roomNumber;
        }

        public String getRoomType() {
            return roomType;
        }

        public Room.Status getStatus() {
            return status;
        }

        public Integer getFloor() {
            return floor;
        }

        public LocalDateTime getNextArrivalAt() {
            return nextArrivalAt;
        }

        public Boolean getActive() {
            return active;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }
    }

/**
     * Immutable optional Room filters passed between controller and service, not persisted Room state.
     */
    public static record RoomSearchCriteria(
            /**
             * Optional exact turnover-status filter.
             */
            Room.Status status,
            /**
             * Optional floor filter checked by RoomService.
             */
            Integer floor,
            /**
             * Optional case-insensitive Room-type filter normalized by RoomService.
             */
            String roomType,
            /**
             * Optional active flag; RoomService restricts STAFF to active Rooms regardless of this filter.
             */
            Boolean active) { }

    /**
     * Writable update room JSON DTO, separate from the entity and returned fields.
     * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
     */
    public static class UpdateRoomRequest {

        /**
         * Operational Room classification used in display and type filtering.
         */
        @NotBlank(message = "Room type is required.")
        @Size(min = 2, max = 50, message = "Room type must be between 2 and 50 characters.")
        private String roomType;

        /**
         * Room floor or optional floor filter; service validation constrains supplied values.
         */
        @NotNull(message = "Floor is required.")
        @Min(value = 1, message = "Floor must be between 1 and 99.")
        @Max(value = 99, message = "Floor must be between 1 and 99.")
        private Integer floor;

        /**
         * Optional server-local arrival readiness time, not a Reservation or Booking relationship.
         */
        // Allows a supplied timestamp at the current time or in the future.
        @FutureOrPresent(message = "Next arrival time must be current or future.")
        private LocalDateTime nextArrivalAt;

        /**
         * Soft-lifecycle flag; inactive rows retain their identity and history.
         */
        @NotNull(message = "Active is required.")
        private Boolean active;

        public String getRoomType() {
            return roomType;
        }

        public void setRoomType(String roomType) {
            this.roomType = roomType;
        }

        public Integer getFloor() {
            return floor;
        }

        public void setFloor(Integer floor) {
            this.floor = floor;
        }

        public LocalDateTime getNextArrivalAt() {
            return nextArrivalAt;
        }

        public void setNextArrivalAt(LocalDateTime nextArrivalAt) {
            this.nextArrivalAt = nextArrivalAt;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }
    }

    /**
     * Writable update room status JSON DTO, separate from the entity and returned fields.
     * Validation checks input shape; the service checks relationships, lifecycle, and permissions.
     */
    public static class UpdateRoomStatusRequest {

        /**
         * Lifecycle enum value interpreted by this resource's service and transition rules.
         */
        @NotNull(message = "Room status is required.")
        private Room.Status status;

        public Room.Status getStatus() {
            return status;
        }

        public void setStatus(Room.Status status) {
            this.status = status;
        }
    }
}

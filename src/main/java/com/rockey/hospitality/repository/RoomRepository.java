package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Room;
import com.rockey.hospitality.entity.RoomStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA supplies standard persistence operations for Room entities through JpaRepository.
 * Domain services use the methods below for filtered reads, eligibility checks, and locked writes where declared.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {

    /**
     * Locks one Room for deactivation and new Task reference checks.
     */
    // Acquires a PESSIMISTIC_WRITE database row lock until the caller's transaction ends.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("SELECT room FROM Room room WHERE room.id = :id")
    java.util.Optional<Room> findByIdForUpdate(
            // Binds this argument as the named id query parameter, not interpolated query text.
            @Param("id") Long id);

    /**
     * Finds active, non-READY Rooms whose arrivals fall within the inclusive readiness window.
     */
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("""
            SELECT room FROM Room room
            WHERE room.active = TRUE AND room.status <> :ready
              AND room.nextArrivalAt >= :now AND room.nextArrivalAt <= :windowEnd
            """)
    List<Room> findReadinessAlertSources(
            // Binds this argument as the named now query parameter, not interpolated query text.
            @Param("now") LocalDateTime now,
                                        // Binds this argument as the named windowEnd query parameter, not interpolated query text.
                                        @Param("windowEnd") LocalDateTime windowEnd,
                                        // Binds this argument as the named ready query parameter, not interpolated query text.
                                        @Param("ready") RoomStatus ready);

    /**
     * Pages optional status, floor, case-insensitive type, and active filters without constructing query text from input.
     */
    // Executes this fixed JPQL query; supplied filter values are bound parameters rather than query text.
    @Query("""
            SELECT room
            FROM Room room
            WHERE (:status IS NULL OR room.status = :status)
              AND (:floor IS NULL OR room.floor = :floor)
              AND (:roomType IS NULL OR LOWER(room.roomType) = LOWER(:roomType))
              AND (:active IS NULL OR room.active = :active)
            """)
    Page<Room> search(
            // Binds this argument as the named status query parameter, not interpolated query text.
            @Param("status") RoomStatus status,
            // Binds this argument as the named floor query parameter, not interpolated query text.
            @Param("floor") Integer floor,
            // Binds this argument as the named roomType query parameter, not interpolated query text.
            @Param("roomType") String roomType,
            // Binds this argument as the named active query parameter, not interpolated query text.
            @Param("active") Boolean active,
            Pageable pageable
    );

    /**
     * Checks global Room-number uniqueness without regard to letter case.
     */
    boolean existsByRoomNumberIgnoreCase(String roomNumber);
}

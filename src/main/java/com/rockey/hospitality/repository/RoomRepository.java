package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Room;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * STUDY NOTE: A Repository is the data-access layer a Service uses to reach database data.
 * JpaRepository lets Spring Data supply standard create/read/update/delete methods without writing basic
 * SQL.
 * RoomService and automation use scoped searches, uniqueness checks, readiness sources and locked lifecycle
 * changes.
 * Spring creates this interface's implementation and sends its queries through JPA/Hibernate to MySQL.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {

    // Repository study key: findBy/existsBy/countBy names are interpreted by Spring Data as queries.
    // @Query supplies fixed JPQL (entity/field-based query text), not string-interpolated user input.
    // @Param binds a Java argument to a named query value instead of inserting it into query text.
    // @Lock(PESSIMISTIC_WRITE) keeps a database row locked until the caller's transaction ends to serialize
    // conflicting changes.

    /**
     * Locks one Room for deactivation and new Task reference checks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT room FROM Room room WHERE room.id = :id")
    java.util.Optional<Room> findByIdForUpdate(
            @Param("id") Long id);

    /**
     * Finds active, non-READY Rooms whose arrivals fall within the inclusive readiness window.
     */
    @Query("""
            SELECT room FROM Room room
            WHERE room.active = TRUE AND room.status <> :ready
              AND room.nextArrivalAt >= :now AND room.nextArrivalAt <= :windowEnd
            """)
    List<Room> findReadinessAlertSources(
            @Param("now") LocalDateTime now,
                                        @Param("windowEnd") LocalDateTime windowEnd,
                                        @Param("ready") Room.Status ready);

    /**
     * Pages optional status, floor, case-insensitive type, and active filters without constructing query text from input.
     */
    @Query("""
            SELECT room
            FROM Room room
            WHERE (:status IS NULL OR room.status = :status)
              AND (:floor IS NULL OR room.floor = :floor)
              AND (:roomType IS NULL OR LOWER(room.roomType) = LOWER(:roomType))
              AND (:active IS NULL OR room.active = :active)
            """)
    Page<Room> search(
            @Param("status") Room.Status status,
            @Param("floor") Integer floor,
            @Param("roomType") String roomType,
            @Param("active") Boolean active,
            Pageable pageable
    );

    /**
     * Checks global Room-number uniqueness without regard to letter case.
     */
    boolean existsByRoomNumberIgnoreCase(String roomNumber);
}

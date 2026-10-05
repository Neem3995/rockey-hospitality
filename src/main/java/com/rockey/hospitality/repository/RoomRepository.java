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

public interface RoomRepository extends JpaRepository<Room, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT room FROM Room room WHERE room.id = :id")
    java.util.Optional<Room> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT room FROM Room room
            WHERE room.active = TRUE AND room.status <> :ready
              AND room.nextArrivalAt >= :now AND room.nextArrivalAt <= :windowEnd
            """)
    List<Room> findReadinessAlertSources(@Param("now") LocalDateTime now,
                                        @Param("windowEnd") LocalDateTime windowEnd,
                                        @Param("ready") RoomStatus ready);

    @Query("""
            SELECT room
            FROM Room room
            WHERE (:status IS NULL OR room.status = :status)
              AND (:floor IS NULL OR room.floor = :floor)
              AND (:roomType IS NULL OR LOWER(room.roomType) = LOWER(:roomType))
              AND (:active IS NULL OR room.active = :active)
            """)
    Page<Room> search(
            @Param("status") RoomStatus status,
            @Param("floor") Integer floor,
            @Param("roomType") String roomType,
            @Param("active") Boolean active,
            Pageable pageable
    );

    boolean existsByRoomNumberIgnoreCase(String roomNumber);
}

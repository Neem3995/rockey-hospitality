package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

/**
 * STUDY NOTE: A repository is the service's database-access interface; Spring Data generates this implementation.
 * JpaRepository supplies save/find operations; derived names such as existsByRoomNumberIgnoreCase become queries.
 * findForUpdate uses @Lock(PESSIMISTIC_WRITE) to hold the room row until the transaction ends.
 * That room lock is the shared gate: task creation, task status changes and inspections for one room wait their turn.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findAllByOrderByRoomNumberAsc();
    boolean existsByRoomNumberIgnoreCase(String roomNumber);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findForUpdate(@Param("id") Long id);
}

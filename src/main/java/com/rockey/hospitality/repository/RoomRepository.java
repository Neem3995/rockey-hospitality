package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

/**
 * STUDY NOTE: This interface tells Spring Data how we need to read and save Room entities.
 * RoomService and TaskService call it with an id or room number; Spring supplies the implementation.
 * {@code JpaRepository<Room, Long>} means the entity is Room and its primary-key type is Long.
 * findForUpdate binds an id into fixed JPQL and requests a write lock until the transaction ends.
 * Task changes and inspections use that room lock to take turns on the same room.
 * This interface does not decide which status move is legal or return an HTTP response.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findAllByOrderByRoomNumberAsc();
    boolean existsByRoomNumberIgnoreCase(String roomNumber);
    // r is an alias for the Room entity, not a table name. :id is bound from the method argument.
    // Spring/Hibernate run this inside the service transaction and return Optional<Room>:
    // an existing locked row, or empty so the service can report 404. This is not a status rule.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findForUpdate(@Param("id") Long id);
}

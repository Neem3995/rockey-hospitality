package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Inspection;
import org.springframework.data.jpa.repository.*;
import java.util.*;

/**
 * STUDY NOTE: A repository is the service's database-access interface; Spring Data generates this implementation.
 * JpaRepository supplies save/find operations; findByRoomIdOrderByIdDesc is a derived query returning a room's
 * inspection history newest first. RoomService locks the room row before inserting, so no lock is needed here.
 */
public interface InspectionRepository extends JpaRepository<Inspection, Long> {
    List<Inspection> findByRoomIdOrderByIdDesc(Long roomId);
}

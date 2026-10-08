package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Inspection;
import org.springframework.data.jpa.repository.*;
import java.util.*;

/**
 * STUDY NOTE: RoomService uses this Spring Data interface to save and read Inspection entities.
 * Given a room id, findByRoomIdOrderByIdDesc returns that room's history with newest ids first.
 * JpaRepository provides the ordinary persistence methods; Hibernate maps the relationships.
 * During inspection creation RoomService already holds the room lock as the workflow gate.
 * This interface does not choose PASS/FAIL, authenticate the inspector or erase old history.
 */
public interface InspectionRepository extends JpaRepository<Inspection, Long> {
    List<Inspection> findByRoomIdOrderByIdDesc(Long roomId);
}

package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Task;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

/**
 * STUDY NOTE: TaskService and work guards call this interface to load Task entities or a room id.
 * Spring Data implements the derived query names; explicit JPQL binds values with @Param.
 * findRoomId locates the room before we lock it, while findForUpdate locks the task afterward.
 * The active-work guard methods also request write locks: in our MySQL setup these are current
 * reads, not older snapshot checks. Locks belong to the calling transaction.
 * This layer fetches work; TaskService still decides ownership and allowed transitions.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findAllByOrderByIdDesc();
    List<Task> findByAssignedUserIdOrderByIdDesc(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Task> findByRoomIdAndStatusIn(Long roomId, Collection<Task.Status> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Task> findByAssignedUserIdAndStatusIn(Long userId, Collection<Task.Status> statuses);
    Optional<Task> findFirstByRoomIdAndStatusOrderByIdDesc(Long roomId, Task.Status status);
    @Query("select t.room.id from Task t where t.id = :id")
    Optional<Long> findRoomId(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> findForUpdate(@Param("id") Long id);
}

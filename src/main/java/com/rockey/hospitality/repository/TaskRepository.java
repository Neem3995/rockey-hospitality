package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Task;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

/**
 * STUDY NOTE: A repository is the service's database-access interface; Spring Data generates this implementation.
 * JpaRepository supplies save/find operations; derived names such as findByAssignedUserIdOrderByIdDesc become queries.
 * @Lock(PESSIMISTIC_WRITE) makes active-work checks locking reads.
 * They see the latest committed rows inside the caller's transaction, not an older snapshot.
 * @Query/@Param supply fixed JPQL with bound values.
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

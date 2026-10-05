package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.Event;
import com.rockey.hospitality.entity.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("""
            SELECT event
            FROM Event event
            WHERE (:status IS NULL OR event.status = :status)
              AND (:dateFrom IS NULL OR event.eventDateTime >= :dateFrom)
              AND (:dateTo IS NULL OR event.eventDateTime <= :dateTo)
            """)
    Page<Event> search(
            @Param("status") EventStatus status,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT event FROM Event event WHERE event.id = :eventId")
    Optional<Event> findByIdForUpdate(@Param("eventId") Long eventId);

    @Query("""
            SELECT COUNT(registeredEvent)
            FROM User user
            JOIN user.registeredEvents registeredEvent
            WHERE registeredEvent.id = :eventId
            """)
    long countRegistrationsByEventId(@Param("eventId") Long eventId);

    @Query("""
            SELECT event FROM Event event
            WHERE event.id IN (
                SELECT registeredEvent.id FROM User user
                JOIN user.registeredEvents registeredEvent
                WHERE user.id = :userId
            )
            """)
    Page<Event> findRegisteredEventsByUserId(
            @Param("userId") Long userId,
            Pageable pageable
    );
}

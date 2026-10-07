package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * STUDY NOTE: A Repository is the data-access layer a Service uses to reach database data.
 * JpaRepository lets Spring Data supply standard create/read/update/delete methods without writing basic
 * SQL.
 * Authentication, Employee and registration services use account lookups, uniqueness checks and membership
 * checks.
 * Spring creates this interface's implementation and sends its queries through JPA/Hibernate to MySQL.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    // Repository study key: findBy/existsBy/countBy names are interpreted by Spring Data as queries.

    /**
     * Finds an account by case-insensitive email for authentication and current-principal loading.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Checks login-email uniqueness without revealing the existing account.
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Finds the current refresh session by hash rather than raw cookie value.
     */
    Optional<User> findByRefreshTokenHash(String refreshTokenHash);

    /**
     * Checks whether one User already holds a join-table membership for an Event.
     */
    boolean existsByIdAndRegisteredEventsId(Long userId, Long eventId);
}

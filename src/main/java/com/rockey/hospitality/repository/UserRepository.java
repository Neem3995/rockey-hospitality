package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA supplies standard persistence operations for User entities through JpaRepository.
 * Domain services use the methods below for filtered reads, eligibility checks, and locked writes where declared.
 */
public interface UserRepository extends JpaRepository<User, Long> {

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

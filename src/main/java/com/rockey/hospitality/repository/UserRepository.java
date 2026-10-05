package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByRefreshTokenHash(String refreshTokenHash);

    boolean existsByIdAndRegisteredEventsId(Long userId, Long eventId);
}

package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

/**
 * STUDY NOTE: A repository is the service's database-access interface; Spring Data generates this implementation.
 * JpaRepository supplies save/find operations; method names such as findByEmailIgnoreCase become queries.
 * @Query gives fixed JPQL, and @Param binds a value into it instead of pasting text into the query.
 * @Lock(PESSIMISTIC_WRITE) holds the user row until the transaction ends.
 * It serializes refresh rotation and account changes, such as deactivating a worker while work is assigned.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    List<User> findByRoleOrderByNameAsc(User.Role role);
    List<User> findAllByOrderByNameAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByRefreshTokenHash(String refreshTokenHash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdate(@Param("id") Long id);
}

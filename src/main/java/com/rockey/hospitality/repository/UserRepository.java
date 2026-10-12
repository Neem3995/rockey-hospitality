package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

/**
 * STUDY NOTE: The auth/team services and account loader use this interface to find User entities.
 * Spring Data turns method names into queries; our explicit JPQL uses bound ids, not pasted input.
 * findForUpdate locks an account for assignment/account changes and for login/refresh/logout.
 * findIdByEmail/findIdByRefreshTokenHash only locate a candidate id without locking; callers then
 * lock that primary key and recheck the email/hash, so every session write locks the row first.
 * The database supplies rows/uniqueness constraints; this interface does not hash passwords
 * or decide what account role a caller can grant.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    org.springframework.data.domain.Page<User> findByRole(User.Role role, org.springframework.data.domain.Pageable pageable);

    Optional<User> findByEmailIgnoreCase(String email);
    /** Non-locking candidate lookups; they never authorize a request on their own. */
    @Query("select u.id from User u where u.email = :email")
    Optional<Long> findIdByEmail(@Param("email") String email);
    @Query("select u.id from User u where u.refreshTokenHash = :hash")
    Optional<Long> findIdByRefreshTokenHash(@Param("hash") String hash);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    List<User> findByRoleOrderByNameAsc(User.Role role);
    List<User> findAllByOrderByNameAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdate(@Param("id") Long id);
}

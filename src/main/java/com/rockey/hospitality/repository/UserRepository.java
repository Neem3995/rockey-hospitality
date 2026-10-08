package com.rockey.hospitality.repository;

import com.rockey.hospitality.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

/**
 * STUDY NOTE: The auth/team services and account loader use this interface to find User entities.
 * Spring Data turns method names into queries; our explicit JPQL uses bound ids, not pasted input.
 * findForUpdate locks an account for assignment/account changes. The refresh-hash lookup is also
 * a locking read, so competing refresh rotations take turns inside their service transactions.
 * The database supplies rows/uniqueness constraints; this interface does not hash passwords
 * or decide what account role a caller can grant.
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

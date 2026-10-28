package com.aloute.repository;

import com.aloute.entity.User;
import com.aloute.security.authorization.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);

    long countByActive(boolean active);
    long countByCreatedAtBetween(Instant start, Instant end);
    long countByRole(Role role);
    long countByRoleAndActive(Role role, boolean active);

    /**
     * Locks every row of the given role with a pessimistic write lock, ordered by id.
     * Used to serialize operations that must preserve "at least one active SYSTEM_ADMIN":
     * concurrent callers acquire the same rows in the same order, so they cannot both
     * observe a two-admin state and both proceed. A database-level lock (not a JVM
     * monitor) is required because the application runs multi-instance.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.role = :role ORDER BY u.id")
    List<User> lockByRoleOrdered(@org.springframework.data.repository.query.Param("role") Role role);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE lower(u.email) = lower(:email)")
    Optional<User> findByEmailForUpdate(@org.springframework.data.repository.query.Param("email") String email);

    @org.springframework.data.jpa.repository.Query("SELECT u.id FROM User u WHERE lower(u.email) = lower(:email)")
    Optional<Long> findIdByEmailIgnoreCase(@org.springframework.data.repository.query.Param("email") String email);
}

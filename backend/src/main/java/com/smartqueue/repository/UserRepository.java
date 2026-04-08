package com.smartqueue.repository;

import com.smartqueue.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // =================================================================================================
    // Concurrency & Row-Level Locking
    // =================================================================================================

    /**
     * Prevents race conditions during critical account modifications.
     * Essential for handling simultaneous requests during password resets, role
     * escalations,
     * or when administrators are toggling the user's active status.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findWithLockById(@Param("id") Long id);

    // =================================================================================================
    // Standard Native Query Lookups
    // =================================================================================================

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByBranchId(Long branchId);

    List<User> findByIsActiveTrue();
}

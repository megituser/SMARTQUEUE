package com.smartqueue.repository;

import com.smartqueue.model.Branch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    // =================================================================================================
    // Concurrency & Row-Level Locking
    // =================================================================================================

    /**
     * Prevents race conditions during simultaneous central administrative updates.
     * Uses pessimistic row-level database locking (SELECT ... FOR UPDATE) to ensure
     * no overlapping transactions overwrite each other's configuration payloads.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Branch b WHERE b.id = :id")
    Optional<Branch> findWithLockById(@Param("id") Long id);

    // =================================================================================================
    // Standard Native Query Lookups
    // =================================================================================================

    Optional<Branch> findByCode(String code);

    List<Branch> findByIsActiveTrue();

    boolean existsByCode(String code);
}

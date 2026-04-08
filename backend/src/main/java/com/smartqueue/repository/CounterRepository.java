package com.smartqueue.repository;

import com.smartqueue.model.Counter;
import com.smartqueue.model.enums.CounterStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CounterRepository extends JpaRepository<Counter, Long> {

    // =================================================================================================
    // Concurrency & Row-Level Locking
    // =================================================================================================

    /**
     * Prevents race conditions when administrators are opening/closing counters or
     * simultaneously allocating a high-priority token to a suddenly available
     * counter.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Counter c WHERE c.id = :id")
    Optional<Counter> findWithLockById(@Param("id") Long id);

    // =================================================================================================
    // Performance Optimized & Custom Queries
    // =================================================================================================

    /**
     * Resolves the dreaded N+1 query issue when rendering dashboard or queue status
     * pages.
     * Uses LEFT JOIN FETCH to eagerly pull the currently active token (if any) and
     * mapped services in a single, highly-optimized database round-trip.
     */
    @Query("""
            SELECT DISTINCT c FROM Counter c
            LEFT JOIN FETCH c.services
            LEFT JOIN FETCH c.currentToken
            WHERE c.branch.id = :branchId
            """)
    List<Counter> findByBranchIdWithDetails(@Param("branchId") Long branchId);

    /**
     * Core matching algorithm: Identifies available, open counters that are
     * explicitly
     * configured to handle a customer's specific requested service and are not
     * actively serving anyone right now.
     */
    @Query("""
            SELECT c FROM Counter c
            JOIN c.services s
            WHERE c.branch.id = :branchId
              AND s.id = :serviceId
              AND c.status = :status
              AND c.currentToken IS NULL
            """)
    List<Counter> findAvailableCountersForService(
            @Param("branchId") Long branchId,
            @Param("serviceId") Long serviceId,
            @Param("status") CounterStatus status);

    // =================================================================================================
    // Standard Native Query Lookups
    // =================================================================================================

    List<Counter> findByBranchId(Long branchId);

    List<Counter> findByBranchIdAndStatus(Long branchId, CounterStatus status);

    int countByBranchIdAndStatus(Long branchId, CounterStatus status);
}

package com.smartqueue.repository;

import com.smartqueue.model.QueueToken;
import com.smartqueue.model.enums.TokenStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QueueTokenRepository extends JpaRepository<QueueToken, Long> {

    // =================================================================================================
    // Concurrency & Row-Level Locking
    // =================================================================================================

    /**
     * Prevents race conditions during state transitions (e.g., Calling, Completing,
     * or No-Showing a token).
     * Critical for ensuring two counters don't accidentally "call" the exact same
     * waiting token simultaneously.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM QueueToken q WHERE q.id = :id")
    Optional<QueueToken> findWithLockById(@Param("id") Long id);

    // =================================================================================================
    // Core Queue Logic & Aggregation
    // =================================================================================================

    List<QueueToken> findByBranchIdAndStatusOrderByPriorityAscIssuedAtAsc(Long branchId, TokenStatus status);

    List<QueueToken> findByBranchIdAndStatusInOrderByPriorityAscIssuedAtAsc(Long branchId, List<TokenStatus> statuses);

    @Query("SELECT COUNT(q) FROM QueueToken q WHERE q.branch.id = :branchId AND q.status = :status")
    int countByBranchIdAndStatus(@Param("branchId") Long branchId, @Param("status") TokenStatus status);

    @Query("SELECT COUNT(q) FROM QueueToken q WHERE q.branch.id = :branchId AND q.issuedAt >= :startOfDay")
    int countTodayTokens(@Param("branchId") Long branchId, @Param("startOfDay") LocalDateTime startOfDay);

    /**
     * Extracts and calculates the highest sequence number used today to ensure
     * sequential token generation.
     * Defensive: Limits scope to "today" to natively allow sequence resets at
     * midnight.
     */
    @Query("""
            SELECT MAX(CAST(SUBSTRING(q.tokenNumber, 3) AS int))
            FROM QueueToken q
            WHERE q.branch.id = :branchId
              AND q.issuedAt >= :startOfDay
            """)
    Integer findMaxTokenSequence(@Param("branchId") Long branchId, @Param("startOfDay") LocalDateTime startOfDay);

    /**
     * Finds waiting tokens for a specific service.
     * Optimization: Uses JOIN FETCH for the service to prevent N+1 queries when
     * building the response payload.
     */
    @Query("""
            SELECT q FROM QueueToken q
            JOIN FETCH q.service
            WHERE q.branch.id = :branchId
              AND q.service.id = :serviceId
              AND q.status = 'WAITING'
            ORDER BY q.priority ASC, q.issuedAt ASC
            """)
    List<QueueToken> findWaitingTokensForService(@Param("branchId") Long branchId, @Param("serviceId") Long serviceId);

    /**
     * Analyzes queue depth to accurately inform a specific token how many people
     * are physically ahead of them.
     */
    @Query("""
            SELECT COUNT(q)
            FROM QueueToken q
            WHERE q.branch.id = :branchId
              AND q.status = 'WAITING'
              AND q.priority <= :priority
              AND q.issuedAt < :issuedAt
            """)
    int countAheadInQueue(
            @Param("branchId") Long branchId,
            @Param("priority") String priority,
            @Param("issuedAt") LocalDateTime issuedAt);

    // =================================================================================================
    // Advanced Next-Token Selection (Pagination Supported)
    // =================================================================================================

    /**
     * Highly optimized method to cleanly fetch the absolute next most eligible
     * token for an empty counter,
     * restricted to the specific services that counter supports.
     */
    @Query("""
            SELECT q FROM QueueToken q
            WHERE q.branch.id = :branchId
              AND q.service.id IN :serviceIds
              AND q.status = 'WAITING'
            ORDER BY q.priority ASC, q.issuedAt ASC
            """)
    List<QueueToken> findNextTokenToCall(
            @Param("branchId") Long branchId,
            @Param("serviceIds") List<Long> serviceIds,
            Pageable pageable);

    // =================================================================================================
    // Analytics & Schedulers
    // =================================================================================================

    List<QueueToken> findByStatusAndCalledAtBefore(TokenStatus status, LocalDateTime calledBefore);

    @Query("""
            SELECT q FROM QueueToken q
            WHERE q.branch.id = :branchId
              AND q.status IN :statuses
              AND q.issuedAt >= :startOfDay
            ORDER BY q.issuedAt DESC
            """)
    List<QueueToken> findTodayTokensByStatuses(
            @Param("branchId") Long branchId,
            @Param("statuses") List<TokenStatus> statuses,
            @Param("startOfDay") LocalDateTime startOfDay);

    /**
     * Calculates the moving average wait time for customers today (Issued ->
     * Serving).
     * Warning: TIMESTAMPDIFF is dialect-dependent (e.g. MySQL/H2). Ensure dialect
     * compatibility natively.
     */
    @Query("""
            SELECT AVG(TIMESTAMPDIFF(MINUTE, q.issuedAt, q.servingStartedAt))
            FROM QueueToken q
            WHERE q.branch.id = :branchId
              AND q.status = 'COMPLETED'
              AND q.issuedAt >= :startOfDay
            """)
    Double findAverageWaitTime(@Param("branchId") Long branchId, @Param("startOfDay") LocalDateTime startOfDay);

    /**
     * Calculates the moving average raw service time for customers today (Serving
     * -> Completed).
     */
    @Query("""
            SELECT AVG(TIMESTAMPDIFF(MINUTE, q.servingStartedAt, q.completedAt))
            FROM QueueToken q
            WHERE q.branch.id = :branchId
              AND q.status = 'COMPLETED'
              AND q.issuedAt >= :startOfDay
            """)
    Double findAverageServiceTime(@Param("branchId") Long branchId, @Param("startOfDay") LocalDateTime startOfDay);
}

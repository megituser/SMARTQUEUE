package com.smartqueue.repository;

import com.smartqueue.model.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // =================================================================================================
    // Concurrency & Row-Level Locking
    // =================================================================================================

    /**
     * Defense against token rotation race conditions (e.g. concurrent automated
     * requests from a SPA
     * resolving in parallel). Locks the specific token record to ensure it is only
     * logically
     * revoked down exactly once to prevent infinite replay loopholes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.token = :token")
    Optional<RefreshToken> findWithLockByToken(@Param("token") String token);

    // =================================================================================================
    // Standard Native Query Lookups
    // =================================================================================================

    Optional<RefreshToken> findByToken(String token);

    // =================================================================================================
    // Bulk Database Mutations
    // =================================================================================================

    /**
     * Efficiently revokes all prior active tokens for a specific user without
     * pulling them
     * into JVM memory first. Perfect for forced lock-outs or heavy password change
     * events.
     * 
     * @return Number of tokens revoked
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.id = :userId")
    int revokeAllByUserId(@Param("userId") Long userId);

    /**
     * Hard-deletes completely useless artifacts from the database.
     * Used exclusively by the maintenance scheduler.
     * 
     * @return Number of physical rows permanently purged
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < CURRENT_TIMESTAMP OR r.revoked = true")
    int deleteExpiredAndRevoked();
}

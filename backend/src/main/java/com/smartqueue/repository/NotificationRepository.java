package com.smartqueue.repository;

import com.smartqueue.model.Notification;
import com.smartqueue.model.enums.NotificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // =================================================================================================
    // Concurrency & Row-Level Locking
    // =================================================================================================

    /**
     * Defense against race conditions in multi-node deployments. If multiple
     * scheduler
     * nodes or worker threads are polling the database for "PENDING" notifications,
     * locking ensures the exact same SMS or Email isn't dispatched twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM Notification n WHERE n.id = :id")
    Optional<Notification> findWithLockById(@Param("id") Long id);

    // =================================================================================================
    // Standard Native Query Lookups
    // =================================================================================================

    List<Notification> findByQueueTokenId(Long tokenId);

    List<Notification> findByStatus(NotificationStatus status);
}

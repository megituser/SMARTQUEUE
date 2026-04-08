package com.smartqueue.repository;

import com.smartqueue.model.ServiceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {

    // =================================================================================================
    // Concurrency & Row-Level Locking
    // =================================================================================================

    /**
     * Prevents race conditions during administrative updates to service
     * configurations
     * (e.g., simultaneously changing average wait time estimations or toggling
     * active status
     * while the system is actively issuing tokens for this service).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ServiceEntity s WHERE s.id = :id")
    Optional<ServiceEntity> findWithLockById(@Param("id") Long id);

    // =================================================================================================
    // Standard Native Query Lookups
    // =================================================================================================

    List<ServiceEntity> findByBranchIdAndIsActiveTrue(Long branchId);

    List<ServiceEntity> findByBranchId(Long branchId);

    boolean existsByBranchIdAndCode(Long branchId, String code);
}

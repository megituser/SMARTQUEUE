package com.smartqueue.repository;

import com.smartqueue.model.Appointment;
import com.smartqueue.model.enums.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // =================================================================================================
    // Concurrency & Row-Level Locking
    // =================================================================================================

    /**
     * Prevents race conditions during check-ins and cancellations.
     * Uses row-level database locking (SELECT ... FOR UPDATE).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appointment a WHERE a.id = :id")
    Optional<Appointment> findWithLockById(@Param("id") Long id);

    // =================================================================================================
    // Native Spring Data Lookups
    // =================================================================================================

    List<Appointment> findByBranchIdAndAppointmentDateAndStatusIn(
            Long branchId, LocalDate date, List<AppointmentStatus> statuses);

    List<Appointment> findByBranchIdAndAppointmentDate(Long branchId, LocalDate date);

    int countByBranchIdAndAppointmentDateAndStatusIn(
            Long branchId, LocalDate date, List<AppointmentStatus> statuses);

    boolean existsByBranchIdAndServiceIdAndAppointmentDateAndStartTimeAndStatusNot(
            Long branchId, Long serviceId, LocalDate date, LocalTime startTime, AppointmentStatus status);

    // =================================================================================================
    // Performance Optimized & Custom Queries
    // =================================================================================================

    /**
     * Fetches active appointments.
     * Defensive: Utilizes JOIN FETCH for relations to prevent N+1 query floods if
     * the
     * caller downstream needs to access the branch or service objects natively.
     */
    @Query("""
            SELECT a FROM Appointment a
            JOIN FETCH a.branch
            JOIN FETCH a.service
            WHERE a.branch.id = :branchId
              AND a.service.id = :serviceId
              AND a.appointmentDate = :date
              AND a.status NOT IN ('CANCELLED')
            """)
    List<Appointment> findActiveAppointments(
            @Param("branchId") Long branchId,
            @Param("serviceId") Long serviceId,
            @Param("date") LocalDate date);

    /**
     * Scheduler query to find appointments needing reminders.
     * Critical Performance Fix: We strictly JOIN FETCH branch and service because
     * the
     * NotificationEvent builder actively calls apt.getBranch().getName() and
     * apt.getService().getName(). Without this, 1,000 reminders = 2,001 database
     * queries.
     */
    @Query("""
            SELECT a FROM Appointment a
            JOIN FETCH a.branch
            JOIN FETCH a.service
            WHERE a.appointmentDate = :date
              AND a.startTime BETWEEN :startTime AND :endTime
              AND a.status = 'BOOKED'
            """)
    List<Appointment> findUpcomingForReminder(
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);

    /**
     * Scheduler query to auto-cancel no-shows based on a moving time window.
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.appointmentDate = :date
              AND a.status = 'BOOKED'
              AND a.startTime < :time
            """)
    List<Appointment> findNoShowCandidates(
            @Param("date") LocalDate date,
            @Param("time") LocalTime time);
}

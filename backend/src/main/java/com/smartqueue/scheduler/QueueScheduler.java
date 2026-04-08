package com.smartqueue.scheduler;

import com.smartqueue.model.Appointment;
import com.smartqueue.model.QueueToken;
import com.smartqueue.model.enums.AppointmentStatus;
import com.smartqueue.model.enums.TokenStatus;
import com.smartqueue.notification.NotificationEvent;
import com.smartqueue.notification.NotificationService;
import com.smartqueue.repository.AppointmentRepository;
import com.smartqueue.repository.QueueTokenRepository;
import com.smartqueue.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueueScheduler {

    private final QueueTokenRepository tokenRepository;
    private final AppointmentRepository appointmentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final NotificationService notificationService;

    @Value("${queue.no-show-timeout-minutes:5}")
    private int noShowTimeoutMinutes;

    /**
     * Detect tokens that were called to a counter but the customer never showed up.
     * Free up the counter automatically.
     */
    // Extracted hardcoded millisecond values to configurable properties for easier
    // testing in lower environments
    @Scheduled(fixedDelayString = "${queue.scheduler.no-show.delay:60000}")
    @Transactional
    public void detectNoShows() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(noShowTimeoutMinutes);
        List<QueueToken> noShows = tokenRepository.findByStatusAndCalledAtBefore(TokenStatus.SERVING, cutoff);

        if (noShows.isEmpty()) {
            return;
        }

        List<QueueToken> updatedTokens = new ArrayList<>();

        for (QueueToken token : noShows) {
            try {
                token.setStatus(TokenStatus.NO_SHOW);
                token.setCompletedAt(LocalDateTime.now());

                // Release the counter back to the pool so other customers can be served
                if (token.getCounter() != null) {
                    token.getCounter().setCurrentToken(null);
                }

                updatedTokens.add(token);
                log.info("Auto no-show applied: Token {} (Called at {}, timeout {} min)",
                        token.getTokenNumber(), token.getCalledAt(), noShowTimeoutMinutes);

            } catch (Exception e) {
                // Defensive fault-isolation: Prevent a single malformed token from aborting the
                // entire cleanup batch
                log.error("Failed to process auto no-show for token {}", token.getId(), e);
            }
        }

        // Batch save to prevent N+1 connection pool exhaustion on the database
        if (!updatedTokens.isEmpty()) {
            tokenRepository.saveAll(updatedTokens);
            log.info("Successfully processed {} no-show tokens", updatedTokens.size());
        }
    }

    /**
     * Send appointment reminders dynamically.
     */
    @Scheduled(fixedDelayString = "${queue.scheduler.reminders.delay:300000}")
    @Transactional(readOnly = true)
    public void sendAppointmentReminders() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalTime reminderWindow = now.plusMinutes(35);

        List<Appointment> upcoming = appointmentRepository.findUpcomingForReminder(today, now, reminderWindow);

        if (upcoming.isEmpty()) {
            return;
        }

        for (Appointment apt : upcoming) {
            try {
                NotificationEvent event = NotificationEvent.builder()
                        .appointmentId(apt.getId())
                        .eventType("APPOINTMENT_REMINDER")
                        .customerName(apt.getCustomerName())
                        .customerPhone(apt.getCustomerPhone())
                        .customerEmail(apt.getCustomerEmail())
                        .branchName(apt.getBranch().getName())
                        .serviceName(apt.getService().getName())
                        .build();

                // Architect Note: Ensure this sendNotification method is executing
                // asynchronously internally
                // (e.g. via @Async or offloaded to a message broker). We DO NOT want
                // third-party API latency
                // (like Twilio/Sendgrid rate limits) to block this core scheduler thread loop.
                notificationService.sendNotification(event);

                log.info("Dispatched reminder notification for appointment {} at {}", apt.getId(), apt.getStartTime());

            } catch (Exception e) {
                log.error("Failed to dispatch reminder for appointment {}. Will retry next cycle.", apt.getId(), e);
            }
        }
    }

    /**
     * Mark pre-booked appointments as no-show if they fail to check-in on time.
     */
    @Scheduled(fixedDelayString = "${queue.scheduler.appointment-no-show.delay:300000}")
    @Transactional
    public void markAppointmentNoShows() {
        LocalDate today = LocalDate.now();
        LocalTime cutoff = LocalTime.now().minusMinutes(15);

        List<Appointment> noShows = appointmentRepository.findNoShowCandidates(today, cutoff);

        if (noShows.isEmpty()) {
            return;
        }

        List<Appointment> updatedAppointments = new ArrayList<>();

        for (Appointment apt : noShows) {
            try {
                apt.setStatus(AppointmentStatus.NO_SHOW);
                updatedAppointments.add(apt);
                log.info("Appointment {} marked as no-show (Scheduled for {})", apt.getId(), apt.getStartTime());
            } catch (Exception e) {
                log.error("Failed to mark appointment {} as no-show", apt.getId(), e);
            }
        }

        if (!updatedAppointments.isEmpty()) {
            appointmentRepository.saveAll(updatedAppointments);
        }
    }

    /**
     * Security/Maintenance: Clean up expired refresh tokens daily at 2 AM server
     * time.
     */
    @Scheduled(cron = "${queue.scheduler.cleanup.cron:0 0 2 * * ?}")
    @Transactional
    public void cleanupExpiredTokens() {
        try {
            refreshTokenRepository.deleteExpiredAndRevoked();
            // Usually traced, but since this fires only once a day, INFO is perfectly fine
            log.info("Routine maintenance: Cleaned up expired/revoked refresh tokens from security database");
        } catch (Exception e) {
            // Mostly occurs if a long manual transaction locks the table or there's a DB
            // deadlock.
            // Doesn't affect uptime immediately, so we just log safely and wait for
            // tomorrow.
            log.warn("Routine token cleanup failed to execute properly", e);
        }
    }
}

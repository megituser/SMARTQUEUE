package com.smartqueue.model;

import com.smartqueue.model.enums.NotificationChannel;
import com.smartqueue.model.enums.NotificationStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_status", columnList = "status"),
        @Index(name = "idx_notifications_queue_token", columnList = "queue_token_id"),
        @Index(name = "idx_notifications_appointment", columnList = "appointment_id"),
        @Index(name = "idx_notifications_sent_at", columnList = "sent_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // At least one context reference is expected — enforced at service layer,
    // not here, since both being null is a usage error, not a schema constraint.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_token_id")
    private QueueToken queueToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String recipient;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Size(max = 255)
    @Column(name = "external_id")
    private String externalId;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isSent() {
        return status == NotificationStatus.SENT && sentAt != null;
    }

    public boolean isPending() {
        return status == NotificationStatus.PENDING;
    }

    public boolean hasFailed() {
        return status == NotificationStatus.FAILED;
    }

    public void markSent(String externalReference) {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.externalId = externalReference;
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
    }
}
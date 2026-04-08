package com.smartqueue.model;

import com.smartqueue.model.enums.TokenPriority;
import com.smartqueue.model.enums.TokenSource;
import com.smartqueue.model.enums.TokenStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "queue_tokens", indexes = {
        @Index(name = "idx_queue_tokens_branch_status", columnList = "branch_id, status"),
        @Index(name = "idx_queue_tokens_branch_issued_at", columnList = "branch_id, issued_at"),
        @Index(name = "idx_queue_tokens_customer_phone", columnList = "customer_phone")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_queue_tokens_branch_token_number", columnNames = { "branch_id", "token_number" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 20)
    @Column(name = "token_number", nullable = false, length = 20)
    private String tokenNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counter_id")
    private Counter counter;

    @Size(max = 100)
    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Size(max = 20)
    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Size(max = 255)
    @Column(name = "customer_email")
    private String customerEmail;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TokenStatus status = TokenStatus.WAITING;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TokenPriority priority = TokenPriority.NORMAL;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TokenSource source = TokenSource.WALK_IN;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @Column(name = "position_in_queue")
    private Integer positionInQueue;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "called_at")
    private LocalDateTime calledAt;

    @Column(name = "serving_started_at")
    private LocalDateTime servingStartedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "estimated_wait_minutes")
    private Integer estimatedWaitMinutes;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public boolean isWaiting() {
        return status == TokenStatus.WAITING;
    }

    public boolean isServing() {
        return status == TokenStatus.SERVING;
    }

    public boolean isCompleted() {
        return status == TokenStatus.COMPLETED;
    }

    public boolean isNoShow() {
        return status == TokenStatus.NO_SHOW;
    }

    public boolean isHighPriority() {
        return priority == TokenPriority.HIGH || priority == TokenPriority.VIP;
    }

    public void markCalled(Counter assignedCounter) {
        this.status = TokenStatus.CALLED;
        this.counter = assignedCounter;
        this.calledAt = LocalDateTime.now();
    }

    public void markServing() {
        this.status = TokenStatus.SERVING;
        this.servingStartedAt = LocalDateTime.now();
    }

    public void markCompleted() {
        this.status = TokenStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markNoShow() {
        this.status = TokenStatus.NO_SHOW;
    }
}
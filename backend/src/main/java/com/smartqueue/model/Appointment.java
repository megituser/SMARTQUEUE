package com.smartqueue.model;

import com.smartqueue.model.enums.AppointmentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "appointments", indexes = {
        @Index(name = "idx_appointments_branch_date", columnList = "branch_id, appointment_date"),
        @Index(name = "idx_appointments_status", columnList = "status"),
        @Index(name = "idx_appointments_customer_phone", columnList = "customer_phone") // supports lookup by phone
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_appointments_branch_service_date_time", columnNames = { "branch_id", "service_id",
                "appointment_date", "start_time" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;

    @NotBlank
    @Size(max = 100)
    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Size(max = 20)
    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Email
    @Size(max = 255)
    @Column(name = "customer_email")
    private String customerEmail;

    @NotNull
    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @NotNull
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @NotNull
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.BOOKED;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_token_id")
    private QueueToken queueToken;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Convenience guard used by service layer before persisting
    public boolean isSlotValid() {
        return startTime != null && endTime != null && endTime.isAfter(startTime);
    }

    public boolean isPending() {
        return status == AppointmentStatus.BOOKED;
    }
}
package com.smartqueue.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AppointmentRequest {

    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotNull(message = "Service ID is required")
    private Long serviceId;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    private String customerPhone;
    private String customerEmail;

    @NotNull(message = "Appointment date is required")
    private LocalDate appointmentDate;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    private String notes;
}

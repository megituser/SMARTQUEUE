package com.smartqueue.dto.response;

import com.smartqueue.model.enums.AppointmentStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AppointmentResponse {
    private Long id;
    private Long branchId;
    private String branchName;
    private Long serviceId;
    private String serviceName;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private AppointmentStatus status;
    private Long queueTokenId;
    private String queueTokenNumber;
    private String notes;
    private LocalDateTime createdAt;
}

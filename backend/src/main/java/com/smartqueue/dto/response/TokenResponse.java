package com.smartqueue.dto.response;

import com.smartqueue.model.enums.TokenPriority;
import com.smartqueue.model.enums.TokenSource;
import com.smartqueue.model.enums.TokenStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TokenResponse {
    private Long id;
    private String tokenNumber;
    private Long branchId;
    private String branchName;
    private Long serviceId;
    private String serviceName;
    private Long counterId;
    private String counterName;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private TokenStatus status;
    private TokenPriority priority;
    private TokenSource source;
    private Integer positionInQueue;
    private LocalDateTime issuedAt;
    private LocalDateTime calledAt;
    private LocalDateTime servingStartedAt;
    private LocalDateTime completedAt;
    private Integer estimatedWaitMinutes;
    private String notes;
}

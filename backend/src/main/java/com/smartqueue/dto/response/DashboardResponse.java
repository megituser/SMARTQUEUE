package com.smartqueue.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DashboardResponse {
    private Long branchId;
    private String branchName;
    private int totalTokensToday;
    private int currentWaiting;
    private int currentServing;
    private int completedToday;
    private int noShowToday;
    private int cancelledToday;
    private double averageWaitMinutes;
    private double averageServiceMinutes;
    private int activeCounters;
    private int totalCounters;
    private int appointmentsToday;
    private int appointmentsCheckedIn;
    private LocalDateTime lastUpdated;
}

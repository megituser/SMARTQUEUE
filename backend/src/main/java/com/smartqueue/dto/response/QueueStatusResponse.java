package com.smartqueue.dto.response;

import com.smartqueue.model.enums.CounterStatus;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class QueueStatusResponse {
    private Long branchId;
    private String branchName;
    private int totalWaiting;
    private int totalServing;
    private int totalCompleted;
    private int averageWaitMinutes;
    private List<TokenResponse> waitingTokens;
    private List<CounterStatusResponse> counters;

    @Data
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class CounterStatusResponse {
        private Long counterId;
        private Integer counterNumber;
        private String counterName;
        private CounterStatus status;
        private TokenResponse currentToken;
        private List<String> serviceNames;
    }
}

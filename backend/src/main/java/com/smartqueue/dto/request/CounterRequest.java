package com.smartqueue.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Set;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CounterRequest {

    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotNull(message = "Counter number is required")
    private Integer counterNumber;

    private String name;

    private Set<Long> serviceIds;
}

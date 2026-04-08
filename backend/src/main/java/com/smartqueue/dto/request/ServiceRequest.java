package com.smartqueue.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;



@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ServiceRequest {

    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotBlank(message = "Service name is required")
    private String name;

    @NotBlank(message = "Service code is required")
    private String code;

    private String description;
    private Integer avgServiceTimeMinutes;
}

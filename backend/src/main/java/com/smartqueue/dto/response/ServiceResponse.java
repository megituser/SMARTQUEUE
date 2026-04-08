package com.smartqueue.dto.response;

import lombok.*;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ServiceResponse {
    private Long id;
    private Long branchId;
    private String name;
    private String code;
    private String description;
    private Integer avgServiceTimeMinutes;
    private Boolean isActive;
}

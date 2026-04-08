package com.smartqueue.dto.request;

import jakarta.validation.constraints.NotBlank;

import lombok.*;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class BranchRequest {

    @NotBlank(message = "Branch name is required")
    private String name;

    @NotBlank(message = "Branch code is required")
    private String code;

    private String address;
    private String phone;
    private String timezone;
}

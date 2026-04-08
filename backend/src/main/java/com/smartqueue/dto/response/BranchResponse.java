package com.smartqueue.dto.response;

import lombok.*;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class BranchResponse {
    private Long id;
    private String name;
    private String code;
    private String address;
    private String phone;
    private String timezone;
    private Boolean isActive;
}

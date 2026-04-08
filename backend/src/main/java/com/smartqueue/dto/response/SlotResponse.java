package com.smartqueue.dto.response;

import lombok.*;

import java.time.LocalTime;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SlotResponse {
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean available;
}

package com.smartqueue.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEvent implements Serializable {

    // Defensive: Essential for stable Java serialization across network boundaries
    // (e.g., if this event is dispatched off to RabbitMQ, Kafka, or Redis pub/sub)
    @Serial
    private static final long serialVersionUID = 1L;

    // Strongly-typed event constants.
    // Eliminates "magic strings" and prevents painful typos in remote schedulers or
    // listeners.
    public static final String EVENT_TOKEN_ISSUED = "TOKEN_ISSUED";
    public static final String EVENT_TOKEN_CALLED = "TOKEN_CALLED";
    public static final String EVENT_APPOINTMENT_REMINDER = "APPOINTMENT_REMINDER";
    public static final String EVENT_APPOINTMENT_CONFIRMATION = "APPOINTMENT_CONFIRMATION";

    // Primary Identifiers
    private Long tokenId;
    private Long appointmentId;

    /**
     * Identifies the orchestration trigger condition for this notification payload.
     * Ideally populated using one of the EVENT_* static constants defined above.
     */
    private String eventType;

    // Customer Contact Details
    private String customerName;
    private String customerPhone;
    private String customerEmail;

    // Queue & Location Presentation Payload
    private String tokenNumber;
    private String branchName;
    private String serviceName;
    private String counterName;
    private Integer counterNumber;

    // Live Analytics Payload
    private Integer positionInQueue;
    private Integer estimatedWaitMinutes;
}

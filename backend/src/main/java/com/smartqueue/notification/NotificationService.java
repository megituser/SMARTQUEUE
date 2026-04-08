package com.smartqueue.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class NotificationService {

    /**
     * Dispatches an event-driven notification.
     * CRITICAL PERFORMANCE FIX: Must be @Async. If we map this to Twilio or AWS SES
     * later,
     * external network latency or API rate limits will utterly block the caller's
     * main
     * database transaction or HTTP request thread if executed synchronously.
     */
    @Async
    public void sendNotification(NotificationEvent event) {
        if (event == null || !StringUtils.hasText(event.getEventType())) {
            log.warn("Dropped malformed notification payload: Null or missing EventType");
            return;
        }

        // Trace level is preferred here natively. Event objects contain massive amounts
        // of PII (names/contacts, etc) which we should never dump blindly into
        // production logs.
        log.trace("Processing notification dispatch. EventType=[{}] TokenId=[{}] AppointmentId=[{}]",
                event.getEventType(),
                event.getTokenId(),
                event.getAppointmentId());

        // Mock routing orchestration based on available customer contact methods
        if (StringUtils.hasText(event.getCustomerEmail())) {
            sendEmail(event.getCustomerEmail(), "SmartQueue Notification", buildGenericMessage(event));
        }

        if (StringUtils.hasText(event.getCustomerPhone())) {
            sendSms(event.getCustomerPhone(), buildGenericMessage(event));
        }
    }

    @Async
    public void sendSms(String phoneNumber, String message) {
        if (!StringUtils.hasText(phoneNumber))
            return;

        // Defensive & Compliance: Mask PII in application logs to prevent
        // GDPR/Compliance violations
        log.info("[MOCK SMS] Dispatch registered to mobile [{}]", obscurePhoneNumber(phoneNumber));
        log.trace("SMS Payload Content: {}", message);
    }

    @Async
    public void sendEmail(String email, String subject, String body) {
        if (!StringUtils.hasText(email))
            return;

        // Defensive & Compliance: Mask PII in application logs
        log.info("[MOCK EMAIL] Dispatch registered to address [{}]. Subject: [{}]", obscureEmail(email), subject);
        log.trace("Email Payload Content: {}", body);
    }

    @Async
    public void sendWhatsApp(String phoneNumber, String message) {
        if (!StringUtils.hasText(phoneNumber))
            return;

        log.info("[MOCK WHATSAPP] Dispatch registered to mobile [{}]", obscurePhoneNumber(phoneNumber));
        log.trace("WhatsApp Payload Content: {}", message);
    }

    // =================================================================================================
    // Internal Utilities (Compliance Masking & Mock Templating)
    // =================================================================================================

    private String buildGenericMessage(NotificationEvent event) {
        String name = StringUtils.hasText(event.getCustomerName()) ? event.getCustomerName() : "Customer";
        return String.format("Hello %s, your queue event [%s] has been registered for branch [%s].",
                name, event.getEventType(), event.getBranchName());
    }

    private String obscurePhoneNumber(String phone) {
        String cleanPhone = phone.trim();
        if (cleanPhone.length() <= 4) {
            return "****";
        }
        return "*******" + cleanPhone.substring(cleanPhone.length() - 4);
    }

    private String obscureEmail(String email) {
        String cleanEmail = email.trim();
        if (!cleanEmail.contains("@")) {
            return "****@****.com";
        }

        String[] parts = cleanEmail.split("@");
        if (parts.length != 2)
            return "****@****.com"; // Defensive catch for bizarre malformed emails

        String name = parts[0];
        String domain = parts[1];

        if (name.length() <= 2) {
            return "*@" + domain;
        }

        // e.g. "john.doe@gmail.com" -> "j****e@gmail.com"
        return name.charAt(0) + "****" + name.charAt(name.length() - 1) + "@" + domain;
    }
}

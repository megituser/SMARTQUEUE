package com.smartqueue.model.enums;

public enum NotificationChannel {
    SMS,
    EMAIL,
    WHATSAPP;

    public boolean requiresPhoneNumber() {
        return this == SMS || this == WHATSAPP;
    }

    public boolean requiresEmail() {
        return this == EMAIL;
    }
}
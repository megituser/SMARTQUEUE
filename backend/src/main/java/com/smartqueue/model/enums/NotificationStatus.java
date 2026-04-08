package com.smartqueue.model.enums;

public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    DELIVERED;

    public boolean isTerminal() {
        return this == DELIVERED || this == FAILED;
    }

    public boolean isSuccessful() {
        return this == SENT || this == DELIVERED;
    }
}
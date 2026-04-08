package com.smartqueue.model.enums;

public enum AppointmentStatus {
    BOOKED,
    CONFIRMED,
    CHECKED_IN,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == NO_SHOW;
    }

    public boolean isActive() {
        return this == BOOKED || this == CONFIRMED || this == CHECKED_IN;
    }
}
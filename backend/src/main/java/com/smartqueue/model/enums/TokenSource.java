package com.smartqueue.model.enums;

public enum TokenSource {
    WALK_IN,
    APPOINTMENT,
    KIOSK,
    ONLINE;

    public boolean isDigital() {
        return this == ONLINE || this == KIOSK;
    }

    public boolean isPreScheduled() {
        return this == APPOINTMENT;
    }
}
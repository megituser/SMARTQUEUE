package com.smartqueue.model.enums;

public enum TokenStatus {
    WAITING,
    CALLED,
    SERVING,
    COMPLETED,
    NO_SHOW,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == NO_SHOW || this == CANCELLED;
    }

    public boolean isActive() {
        return this == WAITING || this == CALLED || this == SERVING;
    }

    public boolean isCallable() {
        return this == WAITING;
    }
}
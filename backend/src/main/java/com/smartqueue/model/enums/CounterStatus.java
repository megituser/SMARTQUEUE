package com.smartqueue.model.enums;

public enum CounterStatus {
    OPEN,
    CLOSED,
    ON_BREAK;

    public boolean isAvailable() {
        return this == OPEN;
    }

    public boolean isOffline() {
        return this == CLOSED || this == ON_BREAK;
    }
}
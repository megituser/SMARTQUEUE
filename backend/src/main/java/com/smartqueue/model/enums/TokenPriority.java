package com.smartqueue.model.enums;

public enum TokenPriority {
    NORMAL(3),
    HIGH(2),
    VIP(1);

    private final int weight;

    TokenPriority(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }

    public boolean outranks(TokenPriority other) {
        return this.weight < other.weight;
    }

    public boolean isElevated() {
        return this == HIGH || this == VIP;
    }
}
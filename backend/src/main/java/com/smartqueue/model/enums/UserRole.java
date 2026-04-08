package com.smartqueue.model.enums;

public enum UserRole {
    SUPER_ADMIN,
    BRANCH_ADMIN,
    STAFF,
    COUNTER_AGENT;

    public boolean isAdmin() {
        return this == SUPER_ADMIN || this == BRANCH_ADMIN;
    }

    public boolean isBranchScoped() {
        return this == BRANCH_ADMIN || this == STAFF || this == COUNTER_AGENT;
    }

    public boolean canManageBranch() {
        return this == SUPER_ADMIN || this == BRANCH_ADMIN;
    }
}
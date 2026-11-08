package com.claimwillow.user;

public enum Role {
    POLICYHOLDER,
    ADJUSTER,
    ADMIN;

    public boolean isStaff() {
        return this == ADJUSTER || this == ADMIN;
    }

    public String authority() {
        return "ROLE_" + name();
    }
}

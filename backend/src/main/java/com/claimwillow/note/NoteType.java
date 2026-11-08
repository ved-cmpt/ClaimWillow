package com.claimwillow.note;

public enum NoteType {
    /** Staff-only working note; never shown to the policyholder. */
    STAFF_INTERNAL,
    /** Adjuster's request for more information (visible to the policyholder). */
    REQUEST_INFO,
    /** Policyholder's answer to a request. */
    POLICYHOLDER_RESPONSE,
    /** Reason or note recorded with an approval/rejection (visible to the policyholder). */
    DECISION;

    public boolean visibleToPolicyholder() {
        return this != STAFF_INTERNAL;
    }
}

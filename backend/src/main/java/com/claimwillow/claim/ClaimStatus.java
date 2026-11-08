package com.claimwillow.claim;

import java.util.EnumSet;
import java.util.Set;

/**
 * Claim lifecycle.
 * <pre>
 * DRAFT -> SUBMITTED -> IN_REVIEW -> (NEEDS_INFO -> IN_REVIEW)*
 * IN_REVIEW -> APPROVED | REJECTED -> CLOSED
 * </pre>
 */
public enum ClaimStatus {
    DRAFT,
    SUBMITTED,
    IN_REVIEW,
    NEEDS_INFO,
    APPROVED,
    REJECTED,
    CLOSED;

    public Set<ClaimStatus> allowedNext() {
        return switch (this) {
            case DRAFT -> EnumSet.of(SUBMITTED);
            case SUBMITTED -> EnumSet.of(IN_REVIEW);
            case IN_REVIEW -> EnumSet.of(NEEDS_INFO, APPROVED, REJECTED);
            case NEEDS_INFO -> EnumSet.of(IN_REVIEW);
            case APPROVED, REJECTED -> EnumSet.of(CLOSED);
            case CLOSED -> EnumSet.noneOf(ClaimStatus.class);
        };
    }

    public boolean canTransitionTo(ClaimStatus next) {
        return allowedNext().contains(next);
    }

    /** Claims staff may pick up and assign. */
    public boolean isOpenForStaff() {
        return this == SUBMITTED || this == IN_REVIEW || this == NEEDS_INFO;
    }

    public boolean isDecided() {
        return this == APPROVED || this == REJECTED;
    }
}

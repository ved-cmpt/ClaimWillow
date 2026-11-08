package com.claimwillow.claim;

import java.util.LinkedHashMap;
import java.util.Map;

import com.claimwillow.common.ApiException;

/** Single place where status transitions are validated. Illegal transitions are HTTP 409. */
public final class ClaimStateMachine {

    private ClaimStateMachine() {
    }

    public static void assertTransition(ClaimStatus from, ClaimStatus to) {
        if (!from.canTransitionTo(to)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("currentStatus", from.name());
            details.put("requestedStatus", to.name());
            details.put("allowedNextStatuses", from.allowedNext().stream().map(Enum::name).toList());
            throw ApiException.conflict("INVALID_TRANSITION",
                    "A claim in status " + from + " cannot move to " + to + ".", details);
        }
    }
}

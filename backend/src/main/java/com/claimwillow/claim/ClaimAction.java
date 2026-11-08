package com.claimwillow.claim;

import java.util.EnumSet;
import java.util.Set;

import com.claimwillow.user.AppUserDetails;
import com.claimwillow.user.Role;

/**
 * Actions a viewer may take on a claim right now. The UI renders only these; the backend still
 * re-checks every rule when the action is performed.
 */
public enum ClaimAction {
    EDIT_DRAFT,
    SUBMIT,
    RESPOND,
    UPLOAD_ATTACHMENT,
    ASSIGN_TO_ME,
    START_REVIEW,
    REQUEST_INFO,
    DECIDE,
    CLOSE,
    ADD_NOTE;

    public static Set<ClaimAction> available(Claim claim, AppUserDetails viewer) {
        Set<ClaimAction> actions = EnumSet.noneOf(ClaimAction.class);
        ClaimStatus status = claim.getStatus();
        if (viewer.role() == Role.POLICYHOLDER) {
            if (!claim.isOwnedBy(viewer.id())) {
                return actions;
            }
            if (status == ClaimStatus.DRAFT) {
                actions.addAll(EnumSet.of(EDIT_DRAFT, SUBMIT, UPLOAD_ATTACHMENT));
            } else if (status == ClaimStatus.NEEDS_INFO) {
                actions.addAll(EnumSet.of(RESPOND, UPLOAD_ATTACHMENT));
            }
            return actions;
        }
        if (status == ClaimStatus.DRAFT) {
            return actions;
        }
        boolean assignedToViewer = viewer.id().equals(claim.getAssignedAdjusterUserId());
        boolean admin = viewer.role() == Role.ADMIN;
        if (status.isOpenForStaff() && !assignedToViewer
                && (claim.getAssignedAdjusterUserId() == null || admin)) {
            actions.add(ASSIGN_TO_ME);
        }
        if (assignedToViewer && status == ClaimStatus.SUBMITTED) {
            actions.add(START_REVIEW);
        }
        if (assignedToViewer && status == ClaimStatus.IN_REVIEW) {
            actions.addAll(EnumSet.of(REQUEST_INFO, DECIDE));
        }
        if (status.isDecided() && (assignedToViewer || admin)) {
            actions.add(CLOSE);
        }
        if (status != ClaimStatus.CLOSED) {
            actions.addAll(EnumSet.of(ADD_NOTE, UPLOAD_ATTACHMENT));
        }
        return actions;
    }
}

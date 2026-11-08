package com.claimwillow.claim;

import com.claimwillow.claim.ClaimDtos.ClaimDetail;
import com.claimwillow.user.AppUserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds the claim detail response, including the open information request when relevant. */
@Component
public class ClaimDetailAssembler {

    private final ClaimViews views;

    public ClaimDetailAssembler(ClaimViews views) {
        this.views = views;
    }

    @Transactional(readOnly = true)
    public ClaimDetail detail(Claim claim, AppUserDetails viewer) {
        return views.detail(claim, viewer, null);
    }
}

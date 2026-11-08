package com.claimwillow.claim;

import java.util.Map;
import java.util.UUID;

import com.claimwillow.common.ApiException;
import com.claimwillow.user.AppUserDetails;
import org.springframework.stereotype.Component;

/**
 * Central claim visibility rules:
 * <ul>
 *   <li>Policyholders see only their own claims (including drafts).</li>
 *   <li>Staff see every submitted claim, never another person's private draft.</li>
 *   <li>Anything not visible is reported as 404 so existence is not leaked.</li>
 * </ul>
 */
@Component
public class ClaimAccess {

    private final ClaimRepository claims;

    public ClaimAccess(ClaimRepository claims) {
        this.claims = claims;
    }

    public Claim visible(UUID claimId, AppUserDetails viewer) {
        Claim claim = claims.findById(claimId).orElseThrow(ClaimAccess::notFound);
        if (!canView(claim, viewer)) {
            throw notFound();
        }
        return claim;
    }

    public Claim owned(UUID claimId, AppUserDetails owner) {
        return claims.findByIdAndOwnerUserId(claimId, owner.id()).orElseThrow(ClaimAccess::notFound);
    }

    public static boolean canView(Claim claim, AppUserDetails viewer) {
        if (viewer.isStaff()) {
            return claim.getStatus() != ClaimStatus.DRAFT;
        }
        return claim.isOwnedBy(viewer.id());
    }

    /** Optimistic concurrency: if the client says which version it saw, it must still be current. */
    public static void checkVersion(Claim claim, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != claim.getVersion()) {
            throw ApiException.conflict("STALE_CLAIM",
                    "This claim was changed by someone else since you opened it. Refresh to see the latest "
                            + "version before trying again.",
                    Map.of("currentVersion", claim.getVersion(), "yourVersion", expectedVersion,
                            "currentStatus", claim.getStatus().name()));
        }
    }

    private static ApiException notFound() {
        return ApiException.notFound("Claim");
    }
}

package com.claimwillow.claim;

import com.claimwillow.claim.ClaimDtos.ClaimDetail;
import com.claimwillow.claim.ClaimDtos.OpenRequest;
import com.claimwillow.note.ClaimNoteRepository;
import com.claimwillow.note.NoteType;
import com.claimwillow.user.AppUserDetails;
import com.claimwillow.user.UserDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds the claim detail response, including the open information request when relevant. */
@Component
public class ClaimDetailAssembler {

    private final ClaimViews views;
    private final ClaimNoteRepository notes;
    private final UserDirectory directory;

    public ClaimDetailAssembler(ClaimViews views, ClaimNoteRepository notes, UserDirectory directory) {
        this.views = views;
        this.notes = notes;
        this.directory = directory;
    }

    @Transactional(readOnly = true)
    public ClaimDetail detail(Claim claim, AppUserDetails viewer) {
        OpenRequest open = null;
        if (claim.getStatus() == ClaimStatus.NEEDS_INFO) {
            open = notes.findFirstByClaimIdAndNoteTypeOrderByCreatedAtDesc(claim.getId(), NoteType.REQUEST_INFO)
                    .map(n -> {
                        UserDirectory.UserRef author = directory.get(n.getAuthorUserId());
                        return new OpenRequest(n.getBody(), author == null ? "Adjuster" : author.displayName(),
                                n.getCreatedAt());
                    })
                    .orElse(null);
        }
        return views.detail(claim, viewer, open);
    }
}

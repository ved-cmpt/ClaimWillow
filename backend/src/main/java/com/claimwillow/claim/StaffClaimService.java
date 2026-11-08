package com.claimwillow.claim;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.claimwillow.audit.AuditService;
import com.claimwillow.audit.EventType;
import com.claimwillow.audit.EventVisibility;
import com.claimwillow.claim.ClaimQueueSpecs.QueueFilter;
import com.claimwillow.claim.StaffDtos.DashboardSummary;
import com.claimwillow.claim.StaffDtos.Decision;
import com.claimwillow.common.ApiException;
import com.claimwillow.note.ClaimNote;
import com.claimwillow.note.ClaimNoteRepository;
import com.claimwillow.note.NoteType;
import com.claimwillow.user.AppUser;
import com.claimwillow.user.AppUserDetails;
import com.claimwillow.user.Role;
import com.claimwillow.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adjuster/admin work on submitted claims. Every state change, its note and its audit event are
 * written in one transaction; the claim's @Version column rejects lost updates.
 */
@Service
public class StaffClaimService {

    private final ClaimRepository claims;
    private final ClaimAccess access;
    private final ClaimNoteRepository notes;
    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;

    public StaffClaimService(ClaimRepository claims, ClaimAccess access, ClaimNoteRepository notes,
            UserRepository users, AuditService audit, Clock clock) {
        this.claims = claims;
        this.access = access;
        this.notes = notes;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Claim> queue(AppUserDetails viewer, List<ClaimStatus> statuses, String assignedTo, String query,
            LocalDate from, LocalDate to, String sort, int page, int size) {
        if (assignedTo != null && !assignedTo.isBlank() && !"me".equals(assignedTo)
                && !"unassigned".equals(assignedTo)) {
            try {
                UUID.fromString(assignedTo);
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("MALFORMED_REQUEST", "assignedTo must be me, unassigned or a user id.");
            }
        }
        Sort order = switch (sort == null ? "" : sort) {
            case "submittedAt,asc" -> Sort.by(Sort.Direction.ASC, "submittedAt");
            case "updatedAt,desc" -> Sort.by(Sort.Direction.DESC, "updatedAt");
            case "estimatedLoss,desc" -> Sort.by(Sort.Direction.DESC, "estimatedLoss");
            default -> Sort.by(Sort.Direction.DESC, "submittedAt");
        };
        QueueFilter filter = new QueueFilter(statuses, assignedTo, viewer.id(), query, from, to);
        return claims.findAll(ClaimQueueSpecs.matching(filter),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), order.and(Sort.by("id"))));
    }

    @Transactional(readOnly = true)
    public DashboardSummary dashboard(AppUserDetails viewer) {
        long newClaims = count(new QueueFilter(List.of(ClaimStatus.SUBMITTED), null, viewer.id(), null, null, null));
        long inReview = count(new QueueFilter(List.of(ClaimStatus.IN_REVIEW), null, viewer.id(), null, null, null));
        long needsInfo = count(new QueueFilter(List.of(ClaimStatus.NEEDS_INFO), null, viewer.id(), null, null, null));
        List<ClaimStatus> open = List.of(ClaimStatus.SUBMITTED, ClaimStatus.IN_REVIEW, ClaimStatus.NEEDS_INFO);
        long mine = count(new QueueFilter(open, "me", viewer.id(), null, null, null));
        long unassigned = count(new QueueFilter(open, "unassigned", viewer.id(), null, null, null));
        return new DashboardSummary(newClaims, inReview, needsInfo, mine, unassigned);
    }

    private long count(QueueFilter filter) {
        return claims.count(ClaimQueueSpecs.matching(filter));
    }

    @Transactional
    public Claim assign(UUID claimId, AppUserDetails actor, Long version, UUID requestedAdjusterId) {
        Claim claim = access.visibleForUpdate(claimId, actor);
        ClaimAccess.checkVersion(claim, version);
        if (!claim.getStatus().isOpenForStaff()) {
            throw ApiException.conflict("INVALID_TRANSITION",
                    "Only submitted, in-review or needs-info claims can be assigned.",
                    Map.of("currentStatus", claim.getStatus().name()));
        }
        UUID target = requestedAdjusterId == null ? actor.id() : requestedAdjusterId;
        if (!target.equals(actor.id()) && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("Adjusters can only assign claims to themselves.");
        }
        AppUser adjuster = users.findById(target).filter(u -> u.getRole().isStaff() && u.isEnabled())
                .orElseThrow(() -> ApiException.badRequest("INVALID_ASSIGNEE", "That user cannot be assigned claims."));
        UUID current = claim.getAssignedAdjusterUserId();
        if (target.equals(current)) {
            throw ApiException.conflict("ALREADY_ASSIGNED", "This claim is already assigned to "
                    + adjuster.getDisplayName() + ".", Map.of("assignedTo", adjuster.getDisplayName()));
        }
        if (current != null && actor.role() != Role.ADMIN) {
            String holder = users.findById(current).map(AppUser::getDisplayName).orElse("another adjuster");
            throw ApiException.conflict("ALREADY_ASSIGNED", "This claim was already taken by " + holder
                    + ". Refresh to see the latest assignment.", Map.of("assignedTo", holder));
        }
        claim.assignTo(target);
        audit.record(claim, actor.id(), EventType.ASSIGNED, null, null,
                "Assigned to " + adjuster.getDisplayName() + ".", EventVisibility.ALL,
                Map.of("adjusterId", target.toString()));
        return claims.saveAndFlush(claim);
    }

    @Transactional
    public Claim startReview(UUID claimId, AppUserDetails actor, Long version) {
        Claim claim = assignedClaim(claimId, actor, version);
        ClaimStatus from = claim.getStatus();
        claim.moveTo(ClaimStatus.IN_REVIEW, Instant.now(clock));
        audit.record(claim, actor.id(), EventType.REVIEW_STARTED, from, ClaimStatus.IN_REVIEW, "Review started.");
        return claims.saveAndFlush(claim);
    }

    @Transactional
    public Claim requestInfo(UUID claimId, AppUserDetails actor, Long version, String reason) {
        Claim claim = assignedClaim(claimId, actor, version);
        String text = requireText(reason, "Explain what information you need.");
        ClaimStatus from = claim.getStatus();
        Instant now = Instant.now(clock);
        claim.moveTo(ClaimStatus.NEEDS_INFO, now);
        notes.save(new ClaimNote(claim.getId(), actor.id(), NoteType.REQUEST_INFO, text, now));
        audit.record(claim, actor.id(), EventType.INFO_REQUESTED, from, ClaimStatus.NEEDS_INFO,
                "Information requested: " + abbreviate(text), EventVisibility.ALL, Map.of());
        return claims.saveAndFlush(claim);
    }

    @Transactional
    public Claim decide(UUID claimId, AppUserDetails actor, Long version, Decision decision, String reason) {
        if (decision == null) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Choose approve or reject.");
        }
        Claim claim = assignedClaim(claimId, actor, version);
        String text = requireText(reason, decision == Decision.APPROVED ? "A decision note is required."
                : "A rejection reason is required.");
        ClaimStatus from = claim.getStatus();
        ClaimStatus to = decision == Decision.APPROVED ? ClaimStatus.APPROVED : ClaimStatus.REJECTED;
        Instant now = Instant.now(clock);
        claim.moveTo(to, now);
        notes.save(new ClaimNote(claim.getId(), actor.id(), NoteType.DECISION, text, now));
        audit.record(claim, actor.id(), EventType.DECISION_RECORDED, from, to,
                (to == ClaimStatus.APPROVED ? "Claim approved: " : "Claim rejected: ") + abbreviate(text),
                EventVisibility.ALL, Map.of("decision", decision.name()));
        return claims.saveAndFlush(claim);
    }

    @Transactional
    public Claim close(UUID claimId, AppUserDetails actor, Long version, String note) {
        Claim claim = access.visibleForUpdate(claimId, actor);
        ClaimAccess.checkVersion(claim, version);
        boolean assignee = actor.id().equals(claim.getAssignedAdjusterUserId());
        if (!assignee && actor.role() != Role.ADMIN) {
            throw notAssignee(claim);
        }
        ClaimStatus from = claim.getStatus();
        Instant now = Instant.now(clock);
        claim.moveTo(ClaimStatus.CLOSED, now);
        String trimmed = note == null ? "" : note.trim();
        if (!trimmed.isEmpty()) {
            notes.save(new ClaimNote(claim.getId(), actor.id(), NoteType.STAFF_INTERNAL, trimmed, now));
        }
        audit.record(claim, actor.id(), EventType.CLOSED, from, ClaimStatus.CLOSED, "Claim closed.");
        return claims.saveAndFlush(claim);
    }

    @Transactional
    public ClaimNote addInternalNote(UUID claimId, AppUserDetails actor, String body) {
        Claim claim = access.visibleForUpdate(claimId, actor);
        if (claim.getStatus() == ClaimStatus.CLOSED) {
            throw ApiException.conflict("CLAIM_CLOSED", "Closed claims cannot receive new notes.",
                    Map.of("currentStatus", claim.getStatus().name()));
        }
        ClaimNote saved = notes.save(new ClaimNote(claim.getId(), actor.id(), NoteType.STAFF_INTERNAL,
                requireText(body, "The note cannot be empty."), Instant.now(clock)));
        audit.record(claim, actor.id(), EventType.NOTE_ADDED, null, null, "Internal note added.",
                EventVisibility.STAFF, Map.of("noteId", saved.getId().toString()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AppUser> adjusters() {
        return users.findByRoleInAndEnabledTrueOrderByDisplayName(List.of(Role.ADJUSTER, Role.ADMIN));
    }

    /** Loads a claim the actor may work on: visible, current version, and assigned to the actor. */
    private Claim assignedClaim(UUID claimId, AppUserDetails actor, Long version) {
        Claim claim = access.visibleForUpdate(claimId, actor);
        ClaimAccess.checkVersion(claim, version);
        if (!actor.id().equals(claim.getAssignedAdjusterUserId())) {
            throw notAssignee(claim);
        }
        return claim;
    }

    private ApiException notAssignee(Claim claim) {
        String detail = claim.getAssignedAdjusterUserId() == null ? "Assign the claim to yourself first."
                : "Only the assigned adjuster can do this. Refresh to see the current assignment.";
        return ApiException.conflict("NOT_ASSIGNEE", detail, Map.of("currentStatus", claim.getStatus().name()));
    }

    static String requireText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw ApiException.badRequest("VALIDATION_FAILED", message);
        }
        return text.trim();
    }

    static String abbreviate(String text) {
        return text.length() <= 200 ? text : text.substring(0, 197) + "...";
    }
}

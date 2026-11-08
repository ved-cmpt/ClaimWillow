package com.claimwillow.claim;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

/** Parameterised criteria for the staff work queue (drafts are always excluded). */
public final class ClaimQueueSpecs {

    private ClaimQueueSpecs() {
    }

    public record QueueFilter(List<ClaimStatus> statuses, String assignedTo, UUID viewerId, String query,
            LocalDate submittedFrom, LocalDate submittedTo) {
    }

    public static Specification<Claim> matching(QueueFilter f) {
        return (root, cq, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.notEqual(root.get("status"), ClaimStatus.DRAFT));
            if (f.statuses() != null && !f.statuses().isEmpty()) {
                p.add(root.get("status").in(f.statuses()));
            }
            if (f.assignedTo() != null && !f.assignedTo().isBlank()) {
                switch (f.assignedTo()) {
                    case "me" -> p.add(cb.equal(root.get("assignedAdjusterUserId"), f.viewerId()));
                    case "unassigned" -> p.add(cb.isNull(root.get("assignedAdjusterUserId")));
                    default -> p.add(cb.equal(root.get("assignedAdjusterUserId"), UUID.fromString(f.assignedTo())));
                }
            }
            if (f.query() != null && !f.query().isBlank()) {
                String like = "%" + f.query().trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("reference")), like, '\\'),
                        cb.like(cb.lower(root.get("title")), like, '\\'),
                        cb.like(cb.lower(root.get("snapshot").get("policyNumber")), like, '\\')));
            }
            if (f.submittedFrom() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("submittedAt"),
                        f.submittedFrom().atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (f.submittedTo() != null) {
                p.add(cb.lessThan(root.get("submittedAt"),
                        f.submittedTo().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}

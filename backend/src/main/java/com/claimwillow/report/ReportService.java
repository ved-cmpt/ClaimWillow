package com.claimwillow.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.claimwillow.claim.Claim;
import com.claimwillow.claim.ClaimDtos.ClaimSummary;
import com.claimwillow.claim.ClaimQueueSpecs;
import com.claimwillow.claim.ClaimQueueSpecs.QueueFilter;
import com.claimwillow.claim.ClaimRepository;
import com.claimwillow.claim.ClaimStatus;
import com.claimwillow.claim.ClaimViews;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Modest reporting straight from the live database (drafts are never included). */
@Service
@Transactional(readOnly = true)
public class ReportService {

    public static final int MAX_EXPORT_ROWS = 10_000;
    static final String[] HEADER = {"reference", "title", "status", "incident_type", "incident_date", "submitted_at_utc",
            "policy_number", "policyholder", "assigned_adjuster", "estimated_loss", "currency", "decided_at_utc",
            "closed_at_utc"};
    private static final DateTimeFormatter INSTANT = DateTimeFormatter.ISO_INSTANT;

    private final ClaimRepository claims;
    private final ClaimViews views;

    public ReportService(ClaimRepository claims, ClaimViews views) {
        this.claims = claims;
        this.views = views;
    }

    public record StatusRow(ClaimStatus status, long count, BigDecimal totalEstimated) {
    }

    public record Summary(List<StatusRow> rows, long totalClaims, BigDecimal totalEstimated) {
    }

    public Summary summary(LocalDate from, LocalDate to) {
        List<Claim> all = claims.findAll(ClaimQueueSpecs.matching(new QueueFilter(null, null, null, null, from, to)));
        Map<ClaimStatus, long[]> counts = new EnumMap<>(ClaimStatus.class);
        Map<ClaimStatus, BigDecimal> totals = new EnumMap<>(ClaimStatus.class);
        for (Claim c : all) {
            counts.computeIfAbsent(c.getStatus(), s -> new long[1])[0]++;
            totals.merge(c.getStatus(), c.getEstimatedLoss(), BigDecimal::add);
        }
        List<StatusRow> rows = new ArrayList<>();
        BigDecimal grand = BigDecimal.ZERO.setScale(2);
        for (ClaimStatus s : ClaimStatus.values()) {
            if (s == ClaimStatus.DRAFT) {
                continue;
            }
            BigDecimal total = totals.getOrDefault(s, BigDecimal.ZERO.setScale(2));
            rows.add(new StatusRow(s, counts.containsKey(s) ? counts.get(s)[0] : 0, total));
            grand = grand.add(total);
        }
        return new Summary(rows, all.size(), grand);
    }

    public String csv(List<ClaimStatus> statuses, LocalDate from, LocalDate to) {
        List<Claim> rows = claims.findAll(ClaimQueueSpecs.matching(new QueueFilter(statuses, null, null, null, from, to)),
                PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by(Sort.Direction.DESC, "submittedAt").and(Sort.by("id"))))
                .getContent();
        List<ClaimSummary> summaries = views.summaries(rows);
        StringBuilder out = new StringBuilder();
        line(out, HEADER);
        for (int i = 0; i < rows.size(); i++) {
            Claim c = rows.get(i);
            ClaimSummary s = summaries.get(i);
            line(out, new String[]{
                    c.getReference(), c.getTitle(), c.getStatus().name(), c.getIncidentType().name(), str(c.getIncidentDate()),
                    instant(c.getSubmittedAt()), s.policyNumber(), s.owner() == null ? "" : s.owner().displayName(),
                    s.assignedAdjuster() == null ? "" : s.assignedAdjuster().displayName(),
                    c.getEstimatedLoss().toPlainString(), c.getCurrency(), instant(c.getDecidedAt()),
                    instant(c.getClosedAt())});
        }
        return out.toString();
    }

    private static void line(StringBuilder out, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(cells[i]));
        }
        out.append("\r\n");
    }

    /**
     * RFC 4180 quoting plus spreadsheet formula-injection protection: cells starting with
     * = + - @ tab or CR are prefixed with a single quote.
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 && !isNumber(v)) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static boolean isNumber(String v) {
        return v.matches("-?\\d+(\\.\\d+)?");
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String instant(java.time.Instant i) {
        return i == null ? "" : INSTANT.format(i.atOffset(ZoneOffset.UTC));
    }
}

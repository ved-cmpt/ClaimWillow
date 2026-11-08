package com.claimwillow.report;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import com.claimwillow.claim.ClaimStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff/reports")
@PreAuthorize("hasAnyRole('ADJUSTER', 'ADMIN')")
@Tag(name = "Reports", description = "Status counts and CSV export from the live database")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/summary")
    @Operation(summary = "Claim counts and estimated totals by status (submitted claims only)")
    public ReportService.Summary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedTo) {
        return reports.summary(submittedFrom, submittedTo);
    }

    @GetMapping(value = "/claims.csv", produces = "text/csv")
    @Operation(summary = "CSV export of submitted claims (filters: status, submitted date range; max 10,000 rows)")
    public ResponseEntity<byte[]> csv(@RequestParam(required = false) List<ClaimStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedTo) {
        byte[] body = reports.csv(status, submittedFrom, submittedTo).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("claimwillow-claims.csv").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(body);
    }
}

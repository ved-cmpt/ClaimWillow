package com.claimwillow.report;

import static com.claimwillow.support.TestFixtures.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.claimwillow.policy.Policy;
import com.claimwillow.support.ClaimApi;
import com.claimwillow.support.IntegrationTest;
import com.claimwillow.support.TestFixtures;
import com.claimwillow.user.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ReportIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestFixtures fixtures;

    @Autowired
    ClaimApi api;

    @Test
    void csvExportReflectsTheDatabaseAndIsSafe() throws Exception {
        AppUser owner = fixtures.policyholder();
        AppUser adjuster = fixtures.adjuster();
        Policy policy = fixtures.activePolicy(owner);
        Map<String, Object> risky = ClaimApi.draft(policy, LocalDate.now().minusDays(1), "LOSS",
                "=HYPERLINK(\"http://example.invalid\",\"click\")", "Phone lost, \"quoted\", with comma", "250.50");
        UUID id = api.submitted(owner, risky);
        api.createDraft(owner, ClaimApi.validDraft(policy));
        String ref = ClaimApi.read(mvc.perform(get("/api/v1/claims/" + id).with(as(owner))), "$.reference");

        String csv = mvc.perform(get("/api/v1/staff/reports/claims.csv?status=SUBMITTED").with(as(adjuster)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("claimwillow-claims.csv")))
                .andReturn().getResponse().getContentAsString();
        List<String> lines = Arrays.asList(csv.split("\r\n"));
        assertThat(lines.getFirst()).startsWith("reference,title,status,incident_type,incident_date");
        String row = lines.stream().filter(l -> l.startsWith(ref)).findFirst().orElseThrow();
        assertThat(row).contains(",SUBMITTED,LOSS,").contains(",250.50,CAD,").contains(policy.getPolicyNumber())
                .contains(owner.getDisplayName());
        assertThat(csv).doesNotContain(",DRAFT,");
        // The formula-like title is neutralised with a leading quote and RFC 4180 quoting.
        assertThat(row).startsWith(ref + ",\"'=HYPERLINK(\"\"http://example.invalid\"\",\"\"click\"\")\",");
        mvc.perform(get("/api/v1/staff/reports/claims.csv").with(as(owner))).andExpect(status().isForbidden());
    }

    @Test
    void summaryCountsByStatus() throws Exception {
        AppUser owner = fixtures.policyholder();
        AppUser adjuster = fixtures.adjuster();
        api.submitted(owner, ClaimApi.validDraft(fixtures.activePolicy(owner)));
        mvc.perform(get("/api/v1/staff/reports/summary").with(as(adjuster)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(6))
                .andExpect(jsonPath("$.rows[?(@.status == 'DRAFT')]").isEmpty())
                .andExpect(jsonPath("$.totalClaims").isNumber());
    }

    @Test
    void adminAccountManagementIsAdminOnly() throws Exception {
        AppUser admin = fixtures.admin();
        AppUser adjuster = fixtures.adjuster();
        AppUser holder = fixtures.policyholder();
        mvc.perform(get("/api/v1/admin/users").with(as(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/admin/users").with(as(adjuster))).andExpect(status().isForbidden());
        api.patchJson("/api/v1/admin/users/" + holder.getId(), admin, Map.of("enabled", false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        api.patchJson("/api/v1/admin/users/" + admin.getId(), admin, Map.of("enabled", false))
                .andExpect(status().isBadRequest());
        api.patchJson("/api/v1/admin/users/" + holder.getId(), adjuster, Map.of("enabled", true))
                .andExpect(status().isForbidden());
    }

    @Test
    void escapeQuotesAndNeutralisesFormulas() {
        assertThat(ReportService.escape("=SUM(A1)")).isEqualTo("'=SUM(A1)");
        assertThat(ReportService.escape("@cmd")).isEqualTo("'@cmd");
        assertThat(ReportService.escape("-12.50")).isEqualTo("-12.50");
        assertThat(ReportService.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(ReportService.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(ReportService.escape(null)).isEmpty();
        assertThat(MediaType.parseMediaType("text/csv").getSubtype()).isEqualTo("csv");
    }
}

package com.claimwillow.claim;

import static com.claimwillow.support.TestFixtures.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.claimwillow.policy.Policy;
import com.claimwillow.policy.PolicyRepository;
import com.claimwillow.support.ClaimApi;
import com.claimwillow.support.IntegrationTest;
import com.claimwillow.support.TestFixtures;
import com.claimwillow.user.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ClaimIntakeIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestFixtures fixtures;

    @Autowired
    ClaimApi api;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PolicyRepository policies;

    @Test
    void draftSaveReopenAndSubmitPersistsSnapshotFindingsAndEvents() throws Exception {
        AppUser owner = fixtures.policyholder();
        Policy policy = fixtures.activePolicy(owner);

        UUID id = api.createDraft(owner, ClaimApi.draft(policy, null, null, "Laptop stolen", null, null));
        mvc.perform(get("/api/v1/claims/" + id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.reference").doesNotExist())
                .andExpect(jsonPath("$.title").value("Laptop stolen"))
                .andExpect(jsonPath("$.availableActions").value(org.hamcrest.Matchers.hasItems("EDIT_DRAFT",
                        "SUBMIT")));

        api.patchJson("/api/v1/claims/" + id + "/draft", owner,
                        ClaimApi.draft(policy, LocalDate.now().minusDays(2), "LOSS", "Laptop stolen",
                                "Short text.", "12000.50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedLoss").value(12000.50));

        api.postJson("/api/v1/claims/" + id + "/submit", owner, Map.of("version", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern(
                        "CW-\\d{4}-[2-9A-Z]{6}")))
                .andExpect(jsonPath("$.policySnapshot.coverageLimit").value(10000.00))
                .andExpect(jsonPath("$.policySnapshot.policyNumber").value(policy.getPolicyNumber()))
                .andExpect(jsonPath("$.availableActions").isEmpty());

        mvc.perform(get("/api/v1/claims/" + id + "/findings").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ruleCode == 'OVER_LIMIT')].shortCode").value("R3"))
                .andExpect(jsonPath("$[?(@.ruleCode == 'INFORMATION_RECOMMENDED')]").value(
                        org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[?(@.ruleCode == 'COVERAGE_REVIEW_REQUIRED')]").isEmpty());

        mvc.perform(get("/api/v1/claims/" + id + "/timeline").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type").value(org.hamcrest.Matchers.contains("DRAFT_CREATED", "SUBMITTED",
                        "VALIDATION_RUN")))
                .andExpect(jsonPath("$[1].fromStatus").value("DRAFT"))
                .andExpect(jsonPath("$[1].toStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$[1].actor.displayName").value(owner.getDisplayName()));

        Integer findingRows = jdbc.queryForObject("select count(*) from claim_findings where claim_id = ?",
                Integer.class, id);
        Integer eventRows = jdbc.queryForObject("select count(*) from claim_events where claim_id = ?",
                Integer.class, id);
        assertThat(findingRows).isEqualTo(3);
        assertThat(eventRows).isEqualTo(3);
    }

    @Test
    void laterPolicyChangesDoNotRewriteTheSnapshot() throws Exception {
        AppUser owner = fixtures.policyholder();
        Policy policy = fixtures.activePolicy(owner);
        UUID id = api.submitted(owner, ClaimApi.validDraft(policy));

        policy.setCoverageLimit(new BigDecimal("99999.00"));
        policies.save(policy);

        mvc.perform(get("/api/v1/claims/" + id).with(as(owner)))
                .andExpect(jsonPath("$.policySnapshot.coverageLimit").value(10000.00));
        assertThatThrownBy(() -> jdbc.update("update claims set snapshot_coverage_limit = 1 where id = ?", id))
                .hasMessageContaining("immutable");
    }

    @Test
    void auditEventsAndFindingsAreAppendOnlyInTheDatabase() throws Exception {
        AppUser owner = fixtures.policyholder();
        UUID id = api.submitted(owner, ClaimApi.validDraft(fixtures.activePolicy(owner)));
        assertThatThrownBy(() -> jdbc.update("update claim_events set summary = 'x' where claim_id = ?", id))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from claim_events where claim_id = ?", id))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from claim_findings where claim_id = ?", id))
                .hasMessageContaining("append-only");
    }

    @Test
    void incompleteDraftCannotBeSubmittedAndStaysUnchanged() throws Exception {
        AppUser owner = fixtures.policyholder();
        Policy policy = fixtures.activePolicy(owner);
        UUID id = api.createDraft(owner, ClaimApi.draft(policy, LocalDate.now().plusDays(5), "DAMAGE", "  ", null,
                "0.00"));
        api.postJson("/api/v1/claims/" + id + "/submit", owner, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "incidentDate", "title", "description", "estimatedLoss")));
        mvc.perform(get("/api/v1/claims/" + id).with(as(owner)))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.reference").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from claim_findings where claim_id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void invalidAmountsAreRejectedWithoutCorruptingTheDraft() throws Exception {
        AppUser owner = fixtures.policyholder();
        Policy policy = fixtures.activePolicy(owner);
        UUID id = api.createDraft(owner, ClaimApi.validDraft(policy));
        for (String bad : List.of("-5.00", "10.123", "99999999999.00")) {
            api.patchJson("/api/v1/claims/" + id + "/draft", owner,
                            ClaimApi.draft(policy, LocalDate.now(), "DAMAGE", "t", "d", bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("estimatedLoss"));
        }
        mvc.perform(post("/api/v1/claims/" + id + "/submit").with(as(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\": \"abc\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/claims/" + id).with(as(owner)))
                .andExpect(jsonPath("$.estimatedLoss").value(1500.00))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void submittedClaimsCannotBeResubmittedOrSilentlyEdited() throws Exception {
        AppUser owner = fixtures.policyholder();
        Policy policy = fixtures.activePolicy(owner);
        UUID id = api.submitted(owner, ClaimApi.validDraft(policy));
        api.postJson("/api/v1/claims/" + id + "/submit", owner, Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
                .andExpect(jsonPath("$.details.currentStatus").value("SUBMITTED"));
        api.patchJson("/api/v1/claims/" + id + "/draft", owner, ClaimApi.validDraft(policy))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_A_DRAFT"));
    }

    @Test
    void staleDraftVersionIsAConflict() throws Exception {
        AppUser owner = fixtures.policyholder();
        Policy policy = fixtures.activePolicy(owner);
        UUID id = api.createDraft(owner, ClaimApi.validDraft(policy));
        Map<String, Object> body = ClaimApi.validDraft(policy);
        body.put("version", 0);
        body.put("title", "Edited title");
        api.patchJson("/api/v1/claims/" + id + "/draft", owner, body)
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        body.put("title", "Edited again from a stale tab");
        api.patchJson("/api/v1/claims/" + id + "/draft", owner, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_CLAIM"));
    }

    @Test
    void ownershipBoundariesAcrossClaimEndpoints() throws Exception {
        AppUser owner = fixtures.policyholder();
        AppUser other = fixtures.policyholder();
        AppUser adjuster = fixtures.adjuster();
        Policy policy = fixtures.activePolicy(owner);
        UUID draftId = api.createDraft(owner, ClaimApi.validDraft(policy));
        UUID submittedId = api.submitted(owner, ClaimApi.validDraft(policy));

        for (String path : List.of("", "/findings", "/timeline")) {
            mvc.perform(get("/api/v1/claims/" + submittedId + path).with(as(other))).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/claims/" + submittedId + path)).andExpect(status().isUnauthorized());
        }
        api.patchJson("/api/v1/claims/" + draftId + "/draft", other, ClaimApi.validDraft(policy))
                .andExpect(status().isNotFound());
        api.postJson("/api/v1/claims/" + draftId + "/submit", other, Map.of()).andExpect(status().isNotFound());

        // Staff never see private drafts, but can read submitted claims.
        mvc.perform(get("/api/v1/claims/" + draftId).with(as(adjuster))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/claims/" + submittedId).with(as(adjuster))).andExpect(status().isOk());
        // Staff cannot act as a claimant.
        api.postJson("/api/v1/claims", adjuster, ClaimApi.validDraft(policy)).andExpect(status().isForbidden());
        api.postJson("/api/v1/claims/" + draftId + "/submit", adjuster, Map.of()).andExpect(status().isForbidden());
        // A claimant cannot reference someone else's policy.
        api.postJson("/api/v1/claims", other, ClaimApi.validDraft(policy)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/claims/mine").with(as(other)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void outOfPeriodIncidentAndDuplicateAreFlaggedNotRejected() throws Exception {
        AppUser owner = fixtures.policyholder();
        Policy policy = fixtures.policy(owner, LocalDate.now().minusMonths(6), LocalDate.now().minusDays(10),
                "10000.00", "500.00");
        Map<String, Object> first = ClaimApi.draft(policy, LocalDate.now().minusDays(4), "DAMAGE", "TV damaged",
                "x".repeat(80), "1000.00");
        UUID firstId = api.submitted(owner, first);
        Map<String, Object> second = ClaimApi.draft(policy, LocalDate.now().minusDays(2), "DAMAGE", "TV damaged again",
                "x".repeat(80), "1100.00");
        UUID secondId = api.submitted(owner, second);

        String firstRef = ClaimApi.read(mvc.perform(get("/api/v1/claims/" + firstId).with(as(owner))), "$.reference");
        mvc.perform(get("/api/v1/claims/" + secondId + "/findings").with(as(owner)))
                .andExpect(jsonPath("$[?(@.ruleCode == 'COVERAGE_REVIEW_REQUIRED')].severity").value("WARNING"))
                .andExpect(jsonPath("$[?(@.ruleCode == 'POTENTIAL_DUPLICATE')].relatedClaim.reference")
                        .value(firstRef));
        mvc.perform(get("/api/v1/claims/" + secondId).with(as(owner)))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void myClaimsIsPaginatedFilteredAndSummarised() throws Exception {
        AppUser owner = fixtures.policyholder();
        Policy policy = fixtures.activePolicy(owner);
        for (int i = 0; i < 3; i++) {
            api.createDraft(owner, ClaimApi.validDraft(policy));
        }
        UUID submittedId = api.submitted(owner, ClaimApi.validDraft(policy));
        mvc.perform(get("/api/v1/claims/mine?size=2&page=0").with(as(owner)))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/claims/mine?status=SUBMITTED").with(as(owner)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(submittedId.toString()));
        String ref = ClaimApi.read(mvc.perform(get("/api/v1/claims/" + submittedId).with(as(owner))), "$.reference");
        mvc.perform(get("/api/v1/claims/mine?query=" + ref.toLowerCase()).with(as(owner)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/claims/mine/summary").with(as(owner)))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.counts[?(@.status == 'DRAFT')].count").value(3));
        mvc.perform(get("/api/v1/claims/mine?status=BOGUS").with(as(owner))).andExpect(status().isBadRequest());
    }

    @Test
    void ruleCatalogIsAvailableToSignedInUsers() throws Exception {
        mvc.perform(get("/api/v1/rules").with(as(fixtures.policyholder())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].shortCode").value("R1"))
                .andExpect(jsonPath("$[0].blocking").value(true));
    }
}

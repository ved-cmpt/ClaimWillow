package com.claimwillow.claim;

import static com.claimwillow.support.TestFixtures.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.claimwillow.policy.Policy;
import com.claimwillow.support.ClaimApi;
import com.claimwillow.support.IntegrationTest;
import com.claimwillow.support.TestFixtures;
import com.claimwillow.user.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class ReviewLifecycleIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestFixtures fixtures;

    @Autowired
    ClaimApi api;

    @Autowired
    JdbcTemplate jdbc;

    private int version(AppUser viewer, UUID id) throws Exception {
        return ClaimApi.read(mvc.perform(get("/api/v1/claims/" + id).with(as(viewer))), "$.version");
    }

    private ResultActions staffPost(AppUser user, UUID id, String action, Map<String, Object> body) throws Exception {
        return api.postJson("/api/v1/staff/claims/" + id + "/" + action, user, body);
    }

    @Test
    void twoRolesCompleteSubmitReviewRequestAnswerDecideClose() throws Exception {
        AppUser owner = fixtures.policyholder();
        AppUser adjuster = fixtures.adjuster();
        Policy policy = fixtures.activePolicy(owner);
        UUID id = api.submitted(owner, ClaimApi.validDraft(policy));

        mvc.perform(get("/api/v1/staff/claims?assignedTo=unassigned&status=SUBMITTED&size=100").with(as(adjuster)))
                .andExpect(jsonPath("$.items[*].id").value(hasItem(id.toString())));

        staffPost(adjuster, id, "assign", Map.of("version", version(adjuster, id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedAdjuster.id").value(adjuster.getId().toString()))
                .andExpect(jsonPath("$.availableActions").value(hasItem("START_REVIEW")));
        staffPost(adjuster, id, "start-review", Map.of("version", version(adjuster, id)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REVIEW"));

        // Reason is mandatory for a request.
        staffPost(adjuster, id, "request-info", Map.of("version", version(adjuster, id), "reason", " "))
                .andExpect(status().isBadRequest());
        staffPost(adjuster, id, "request-info", Map.of("version", version(adjuster, id),
                        "reason", "Please upload photos of the damaged sofa and the plumber invoice."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEEDS_INFO"));

        mvc.perform(get("/api/v1/claims/" + id).with(as(owner)))
                .andExpect(jsonPath("$.openRequest.message").value(
                        "Please upload photos of the damaged sofa and the plumber invoice."))
                .andExpect(jsonPath("$.availableActions").value(hasItem("RESPOND")));

        // An empty response without new evidence is rejected; a written answer is accepted.
        api.postJson("/api/v1/claims/" + id + "/responses", owner, Map.of("message", "  "))
                .andExpect(status().isBadRequest());
        api.postJson("/api/v1/claims/" + id + "/responses", owner,
                        Map.of("version", version(owner, id), "message", "The plumber invoice number is PL-2291."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REVIEW"));

        // Approval needs a decision note.
        staffPost(adjuster, id, "decision", Map.of("version", version(adjuster, id), "decision", "APPROVED"))
                .andExpect(status().isBadRequest());
        staffPost(adjuster, id, "decision", Map.of("version", version(adjuster, id), "decision", "APPROVED",
                        "reason", "Reviewed invoice and photos; within policy terms."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedAt").exists());
        staffPost(adjuster, id, "close", Map.of("version", version(adjuster, id)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").exists())
                .andExpect(jsonPath("$.availableActions").isEmpty());

        mvc.perform(get("/api/v1/claims/" + id + "/timeline").with(as(owner)))
                .andExpect(jsonPath("$[*].type").value(contains("DRAFT_CREATED", "SUBMITTED", "VALIDATION_RUN",
                        "ASSIGNED", "REVIEW_STARTED", "INFO_REQUESTED", "RESPONSE_SUBMITTED", "VALIDATION_RUN",
                        "DECISION_RECORDED", "CLOSED")))
                .andExpect(jsonPath("$[7].summary").value(org.hamcrest.Matchers.containsString("re-ran")));
        mvc.perform(get("/api/v1/claims/" + id + "/notes").with(as(owner)))
                .andExpect(jsonPath("$[*].type").value(contains("REQUEST_INFO", "POLICYHOLDER_RESPONSE", "DECISION")));
        Integer statusEvents = jdbc.queryForObject(
                "select count(*) from claim_events where claim_id = ? and to_status is not null", Integer.class, id);
        assertThat(statusEvents).isEqualTo(7);
    }

    @Test
    void rejectionRequiresReasonAndIllegalTransitionsAreConflicts() throws Exception {
        AppUser owner = fixtures.policyholder();
        AppUser adjuster = fixtures.adjuster();
        UUID id = api.submitted(owner, ClaimApi.validDraft(fixtures.activePolicy(owner)));
        staffPost(adjuster, id, "assign", Map.of("version", version(adjuster, id))).andExpect(status().isOk());

        // Cannot decide or request info before review starts; cannot close an undecided claim.
        staffPost(adjuster, id, "decision", Map.of("version", version(adjuster, id), "decision", "REJECTED",
                "reason", "Not covered by the policy terms.")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        staffPost(adjuster, id, "close", Map.of("version", version(adjuster, id))).andExpect(status().isConflict());
        // Owner cannot respond when no information was requested.
        api.postJson("/api/v1/claims/" + id + "/responses", owner, Map.of("message", "hello there"))
                .andExpect(status().isConflict());

        staffPost(adjuster, id, "start-review", Map.of("version", version(adjuster, id))).andExpect(status().isOk());
        staffPost(adjuster, id, "start-review", Map.of("version", version(adjuster, id)))
                .andExpect(status().isConflict());
        staffPost(adjuster, id, "decision", Map.of("version", version(adjuster, id), "decision", "REJECTED",
                "reason", "")).andExpect(status().isBadRequest());
        staffPost(adjuster, id, "decision", Map.of("version", version(adjuster, id), "decision", "REJECTED",
                "reason", "Items are excluded under the policy terms.")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        // Decisions are final.
        staffPost(adjuster, id, "decision", Map.of("version", version(adjuster, id), "decision", "APPROVED",
                "reason", "Changing my mind after rejection.")).andExpect(status().isConflict());
        staffPost(adjuster, id, "request-info", Map.of("version", version(adjuster, id),
                "reason", "Trying to reopen a decided claim.")).andExpect(status().isConflict());
    }

    @Test
    void staleVersionAndSecondAdjusterGetActionableConflicts() throws Exception {
        AppUser owner = fixtures.policyholder();
        AppUser dana = fixtures.adjuster();
        AppUser sam = fixtures.adjuster();
        UUID id = api.submitted(owner, ClaimApi.validDraft(fixtures.activePolicy(owner)));
        int seenByBoth = version(dana, id);

        staffPost(dana, id, "assign", Map.of("version", seenByBoth)).andExpect(status().isOk());
        staffPost(sam, id, "assign", Map.of("version", seenByBoth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_CLAIM"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Refresh")));
        staffPost(sam, id, "assign", Map.of("version", version(sam, id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_ASSIGNED"));
        // Sam cannot act on Dana's claim.
        staffPost(sam, id, "start-review", Map.of("version", version(sam, id)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_ASSIGNEE"));
        mvc.perform(get("/api/v1/claims/" + id).with(as(sam)))
                .andExpect(jsonPath("$.assignedAdjuster.id").value(dana.getId().toString()))
                .andExpect(jsonPath("$.availableActions").value(not(hasItem("ASSIGN_TO_ME"))));
    }

    @Test
    void concurrentAssignmentsProduceExactlyOneWinner() throws Exception {
        AppUser owner = fixtures.policyholder();
        UUID id = api.submitted(owner, ClaimApi.validDraft(fixtures.activePolicy(owner)));
        List<AppUser> adjusters = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            adjusters.add(fixtures.adjuster());
        }
        int v = version(owner, id);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(adjusters.size());
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (AppUser adjuster : adjusters) {
                Callable<Integer> task = () -> {
                    start.await();
                    return staffPost(adjuster, id, "assign", Map.of("version", v)).andReturn().getResponse()
                            .getStatus();
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == 409).hasSize(adjusters.size() - 1);
        } finally {
            pool.shutdownNow();
        }
        Integer assignedEvents = jdbc.queryForObject(
                "select count(*) from claim_events where claim_id = ? and event_type = 'ASSIGNED'", Integer.class, id);
        assertThat(assignedEvents).isEqualTo(1);
    }

    @Test
    void internalNotesAreStaffOnlyAndStaffEndpointsAreRoleProtected() throws Exception {
        AppUser owner = fixtures.policyholder();
        AppUser adjuster = fixtures.adjuster();
        UUID id = api.submitted(owner, ClaimApi.validDraft(fixtures.activePolicy(owner)));
        api.postJson("/api/v1/staff/claims/" + id + "/notes", adjuster, Map.of("body", "Call the plumber to verify."))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/claims/" + id + "/notes").with(as(adjuster)))
                .andExpect(jsonPath("$[0].type").value("STAFF_INTERNAL"));
        mvc.perform(get("/api/v1/claims/" + id + "/notes").with(as(owner)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/claims/" + id + "/timeline").with(as(owner)))
                .andExpect(jsonPath("$[*].type").value(not(hasItem("NOTE_ADDED"))));
        mvc.perform(get("/api/v1/claims/" + id + "/timeline").with(as(adjuster)))
                .andExpect(jsonPath("$[*].type").value(hasItem("NOTE_ADDED")));

        // Policyholders (even the owner) cannot call staff actions; anonymous gets 401.
        for (String action : List.of("assign", "start-review", "request-info", "decision", "close", "notes")) {
            api.postJson("/api/v1/staff/claims/" + id + "/" + action, owner, Map.of("version", 0, "body", "x"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/staff/claims")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/staff/dashboard/summary").with(as(owner))).andExpect(status().isForbidden());
        // Unknown claim ids are 404 for staff too.
        staffPost(adjuster, UUID.randomUUID(), "assign", Map.of("version", 0)).andExpect(status().isNotFound());
        // Adjusters cannot assign other people; admins can.
        AppUser other = fixtures.adjuster();
        staffPost(adjuster, id, "assign", Map.of("version", version(adjuster, id), "adjusterId", other.getId()))
                .andExpect(status().isForbidden());
        AppUser admin = fixtures.admin();
        staffPost(admin, id, "assign", Map.of("version", version(admin, id), "adjusterId", other.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedAdjuster.id").value(other.getId().toString()));
    }

    @Test
    void queueFiltersPaginatesAndCountsAgainstTheDatabase() throws Exception {
        AppUser owner = fixtures.policyholder();
        AppUser adjuster = fixtures.adjuster();
        Policy policy = fixtures.activePolicy(owner);
        UUID a = api.submitted(owner, ClaimApi.validDraft(policy));
        UUID b = api.submitted(owner, ClaimApi.validDraft(policy));
        api.createDraft(owner, ClaimApi.validDraft(policy));
        staffPost(adjuster, a, "assign", Map.of("version", version(adjuster, a))).andExpect(status().isOk());

        mvc.perform(get("/api/v1/staff/claims?assignedTo=me").with(as(adjuster)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(a.toString()))
                .andExpect(jsonPath("$.items[0].assignedAdjuster.displayName").value(adjuster.getDisplayName()));
        mvc.perform(get("/api/v1/staff/claims?query=" + policy.getPolicyNumber()).with(as(adjuster)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[*].status").value(not(hasItem("DRAFT"))));
        mvc.perform(get("/api/v1/staff/claims?query=" + policy.getPolicyNumber() + "&size=1&page=1")
                        .with(as(adjuster)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
        String ref = ClaimApi.read(mvc.perform(get("/api/v1/claims/" + b).with(as(adjuster))), "$.reference");
        mvc.perform(get("/api/v1/staff/claims?query=" + ref).with(as(adjuster)))
                .andExpect(jsonPath("$.items[0].id").value(b.toString()));
        mvc.perform(get("/api/v1/staff/claims?status=SUBMITTED&status=IN_REVIEW&query=" + policy.getPolicyNumber())
                        .with(as(adjuster)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/staff/claims?assignedTo=not-a-uuid").with(as(adjuster)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/staff/dashboard/summary").with(as(adjuster)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToMe").value(1))
                .andExpect(jsonPath("$.newClaims").isNumber());
    }
}

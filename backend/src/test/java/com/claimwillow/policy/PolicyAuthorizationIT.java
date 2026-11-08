package com.claimwillow.policy;

import static com.claimwillow.support.TestFixtures.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claimwillow.support.IntegrationTest;
import com.claimwillow.support.TestFixtures;
import com.claimwillow.user.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class PolicyAuthorizationIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestFixtures fixtures;

    @Test
    void ownersSeeOnlyTheirOwnPolicies() throws Exception {
        AppUser alice = fixtures.policyholder();
        AppUser bob = fixtures.policyholder();
        Policy a1 = fixtures.activePolicy(alice);
        fixtures.activePolicy(alice);
        Policy b1 = fixtures.activePolicy(bob);

        mvc.perform(get("/api/v1/policies/mine").with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == '" + b1.getId() + "')]").isEmpty())
                .andExpect(jsonPath("$[0].currency").value("CAD"));

        mvc.perform(get("/api/v1/policies/" + a1.getId()).with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyNumber").value(a1.getPolicyNumber()))
                .andExpect(jsonPath("$.coverageLimit").value(10000.00));
    }

    @Test
    void anotherOwnersPolicyLooksMissing() throws Exception {
        AppUser alice = fixtures.policyholder();
        AppUser bob = fixtures.policyholder();
        Policy b1 = fixtures.activePolicy(bob);
        mvc.perform(get("/api/v1/policies/" + b1.getId()).with(as(alice)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void staffCanReadPoliciesButCannotActAsClaimant() throws Exception {
        AppUser holder = fixtures.policyholder();
        Policy policy = fixtures.activePolicy(holder);
        AppUser adjuster = fixtures.adjuster();
        mvc.perform(get("/api/v1/policies/" + policy.getId()).with(as(adjuster))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/policies/mine").with(as(adjuster)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void anonymousIsUnauthorizedAndPolicyholderIsForbiddenFromStaffArea() throws Exception {
        mvc.perform(get("/api/v1/policies/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/staff/claims").with(as(fixtures.policyholder())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").with(as(fixtures.adjuster())))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/policies/not-a-uuid").with(as(fixtures.policyholder())))
                .andExpect(status().isBadRequest());
    }
}

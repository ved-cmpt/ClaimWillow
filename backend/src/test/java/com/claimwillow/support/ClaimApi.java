package com.claimwillow.support;

import static com.claimwillow.support.TestFixtures.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.claimwillow.policy.Policy;
import com.claimwillow.user.AppUser;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** Small helper for driving the claim API in integration tests. */
@Component
public class ClaimApi {

    private final MockMvc mvc;
    private final JsonMapper json;

    public ClaimApi(MockMvc mvc, JsonMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public static Map<String, Object> draft(Policy policy, LocalDate incident, String type, String title,
            String description, String estimate) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("policyId", policy == null ? null : policy.getId().toString());
        m.put("incidentDate", incident == null ? null : incident.toString());
        m.put("incidentType", type);
        m.put("title", title);
        m.put("description", description);
        m.put("estimatedLoss", estimate);
        return m;
    }

    public static Map<String, Object> validDraft(Policy policy) {
        return draft(policy, LocalDate.now().minusDays(3), "DAMAGE", "Burst pipe damaged sofa",
                "A pipe under the kitchen sink burst overnight and soaked the living room sofa and rug.",
                "1500.00");
    }

    public String toJson(Object body) {
        return json.writeValueAsString(body);
    }

    public ResultActions postJson(String url, AppUser user, Object body) throws Exception {
        return mvc.perform(post(url).with(as(user)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)));
    }

    public ResultActions patchJson(String url, AppUser user, Object body) throws Exception {
        return mvc.perform(patch(url).with(as(user)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)));
    }

    public UUID createDraft(AppUser owner, Map<String, Object> body) throws Exception {
        String response = postJson("/api/v1/claims", owner, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    /** Creates and submits a claim; returns its id. */
    public UUID submitted(AppUser owner, Map<String, Object> body) throws Exception {
        UUID id = createDraft(owner, body);
        postJson("/api/v1/claims/" + id + "/submit", owner, Map.of()).andExpect(status().isOk());
        return id;
    }

    public static <T> T read(ResultActions actions, String path) throws Exception {
        return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), path);
    }
}

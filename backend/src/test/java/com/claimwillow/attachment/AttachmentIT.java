package com.claimwillow.attachment;

import static com.claimwillow.support.TestFixtures.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import com.claimwillow.support.ClaimApi;
import com.claimwillow.support.IntegrationTest;
import com.claimwillow.support.TestFixtures;
import com.claimwillow.user.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class AttachmentIT {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
    static final byte[] PDF = "%PDF-1.4\n% synthetic test evidence\n%%EOF\n".getBytes();

    @Autowired
    MockMvc mvc;

    @Autowired
    TestFixtures fixtures;

    @Autowired
    ClaimApi api;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions upload(AppUser user, UUID claimId, String filename, String declaredType, byte[] bytes)
            throws Exception {
        return mvc.perform(multipart("/api/v1/claims/" + claimId + "/attachments")
                .file(new MockMultipartFile("file", filename, declaredType, bytes))
                .with(as(user)).with(csrf()));
    }

    @Test
    void ownerUploadsToDraftAndOnlyAuthorizedUsersCanDownload() throws Exception {
        AppUser owner = fixtures.policyholder();
        AppUser other = fixtures.policyholder();
        AppUser adjuster = fixtures.adjuster();
        UUID id = api.createDraft(owner, ClaimApi.validDraft(fixtures.activePolicy(owner)));

        String body = upload(owner, id, "sofa photo.png", "image/png", PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.filename").value("sofa photo.png"))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String attachmentId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        String url = "/api/v1/claims/" + id + "/attachments/" + attachmentId;

        mvc.perform(get(url).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().bytes(PNG));
        mvc.perform(get(url).with(as(other))).andExpect(status().isNotFound());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        // Staff cannot see draft evidence; after submission they can.
        mvc.perform(get(url).with(as(adjuster))).andExpect(status().isNotFound());
        api.postJson("/api/v1/claims/" + id + "/submit", owner, Map.of()).andExpect(status().isOk());
        mvc.perform(get(url).with(as(adjuster))).andExpect(status().isOk());
        // Attachment ids are scoped to their claim.
        UUID otherClaim = api.createDraft(owner, ClaimApi.validDraft(fixtures.activePolicy(owner)));
        mvc.perform(get("/api/v1/claims/" + otherClaim + "/attachments/" + attachmentId).with(as(owner)))
                .andExpect(status().isNotFound());

        // Evidence attached before submission satisfies R6's evidence check.
        mvc.perform(get("/api/v1/claims/" + id + "/findings").with(as(owner)))
                .andExpect(jsonPath("$[*].explanation").value(org.hamcrest.Matchers.not(
                        hasItem(containsString("No evidence")))));
        mvc.perform(get("/api/v1/claims/" + id + "/timeline").with(as(owner)))
                .andExpect(jsonPath("$[*].type").value(hasItem("ATTACHMENT_ADDED")));
        String storageKey = jdbc.queryForObject("select storage_key from claim_attachments where id = ?::uuid",
                String.class, attachmentId);
        assertThat(storageKey).doesNotContain("sofa").matches("[a-f0-9-]{36}");
    }

    @Test
    void invalidTypesAreRejectedByContentNotByDeclaredType() throws Exception {
        AppUser owner = fixtures.policyholder();
        UUID id = api.createDraft(owner, ClaimApi.validDraft(fixtures.activePolicy(owner)));
        upload(owner, id, "script.png", "image/png", "<script>alert(1)</script>".getBytes())
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        upload(owner, id, "tool.exe", "application/octet-stream", new byte[]{'M', 'Z', 0, 0})
                .andExpect(status().isUnsupportedMediaType());
        upload(owner, id, "report.png", "image/png", PDF).andExpect(status().isUnsupportedMediaType());
        upload(owner, id, "empty.pdf", "application/pdf", new byte[0]).andExpect(status().isBadRequest());
        upload(owner, id, "receipt.pdf", "text/plain", PDF).andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("application/pdf"));
        upload(owner, id, "../../etc/passwd.pdf", "application/pdf", PDF).andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("passwd.pdf"));
    }

    @Test
    void oversizedFilesAreRejected() throws Exception {
        AppUser owner = fixtures.policyholder();
        UUID id = api.createDraft(owner, ClaimApi.validDraft(fixtures.activePolicy(owner)));
        byte[] big = Arrays.copyOf(PDF, 5 * 1024 * 1024 + 1);
        upload(owner, id, "big.pdf", "application/pdf", big)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void uploadsFollowRoleAndStatusRules() throws Exception {
        AppUser owner = fixtures.policyholder();
        AppUser other = fixtures.policyholder();
        AppUser adjuster = fixtures.adjuster();
        UUID id = api.submitted(owner, ClaimApi.validDraft(fixtures.activePolicy(owner)));
        // Submitted and not waiting for information: the owner cannot add files silently.
        upload(owner, id, "late.pdf", "application/pdf", PDF).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_ALLOWED"));
        upload(other, id, "x.pdf", "application/pdf", PDF).andExpect(status().isNotFound());
        // Staff can attach to an open claim.
        upload(adjuster, id, "inspection.pdf", "application/pdf", PDF).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/claims/" + id + "/attachments").with(as(owner)))
                .andExpect(jsonPath("$[0].uploadedBy").value(adjuster.getDisplayName()))
                .andExpect(jsonPath("$[0].downloadUrl").value(containsString("/attachments/")));
        // Upload without CSRF token is refused.
        mvc.perform(multipart("/api/v1/claims/" + id + "/attachments")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", PDF)).with(as(adjuster)))
                .andExpect(status().isForbidden());
    }

    @Test
    void attachmentAfterInformationRequestCountsAsAResponse() throws Exception {
        AppUser owner = fixtures.policyholder();
        AppUser adjuster = fixtures.adjuster();
        UUID id = api.submitted(owner, ClaimApi.validDraft(fixtures.activePolicy(owner)));
        int v = ClaimApi.read(mvc.perform(get("/api/v1/claims/" + id).with(as(adjuster))), "$.version");
        api.postJson("/api/v1/staff/claims/" + id + "/assign", adjuster, Map.of("version", v));
        v = ClaimApi.read(mvc.perform(get("/api/v1/claims/" + id).with(as(adjuster))), "$.version");
        api.postJson("/api/v1/staff/claims/" + id + "/start-review", adjuster, Map.of("version", v));
        v = ClaimApi.read(mvc.perform(get("/api/v1/claims/" + id).with(as(adjuster))), "$.version");
        api.postJson("/api/v1/staff/claims/" + id + "/request-info", adjuster,
                Map.of("version", v, "reason", "Please attach the purchase receipt.")).andExpect(status().isOk());

        upload(owner, id, "receipt.pdf", "application/pdf", PDF).andExpect(status().isCreated());
        api.postJson("/api/v1/claims/" + id + "/responses", owner, Map.of("message", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));
        mvc.perform(get("/api/v1/claims/" + id + "/timeline").with(as(adjuster)))
                .andExpect(jsonPath("$[?(@.type == 'RESPONSE_SUBMITTED')].summary")
                        .value(hasItem(containsString("1 new attachment"))));
    }
}

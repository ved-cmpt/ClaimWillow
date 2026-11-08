package com.claimwillow.attachment;

import static com.claimwillow.support.TestFixtures.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.claimwillow.support.ClaimApi;
import com.claimwillow.support.IntegrationTest;
import com.claimwillow.user.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs every {@link AttachmentIT} scenario again with ATTACHMENT_STORAGE_MODE=database, then checks
 * what is specific to keeping the bytes in PostgreSQL.
 */
@IntegrationTest
@TestPropertySource(properties = "app.attachments.storage-mode=database")
class DatabaseAttachmentStorageIT extends AttachmentIT {

    @Autowired
    AttachmentStorage storage;

    @Autowired
    TransactionTemplate tx;

    @Value("${app.attachments.local-dir}")
    String localDir;

    private int blobCount(UUID claimId) {
        return jdbc.queryForObject("select count(*) from attachment_blobs b join claim_attachments a "
                + "on a.storage_key = b.storage_key where a.claim_id = ?", Integer.class, claimId);
    }

    @Test
    void databaseModeIsActive() {
        assertThat(storage).isInstanceOf(DatabaseAttachmentStorage.class);
    }

    @Test
    void bytesAreStoredInPostgresAndNotOnDisk() throws Exception {
        AppUser owner = fixtures.policyholder();
        UUID id = api.createDraft(owner, ClaimApi.validDraft(fixtures.activePolicy(owner)));
        String body = upload(owner, id, "receipt.pdf", "application/pdf", PDF)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String attachmentId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        String key = jdbc.queryForObject("select storage_key from claim_attachments where id = ?::uuid",
                String.class, attachmentId);

        assertThat(jdbc.queryForObject("select octet_length(content) from attachment_blobs where storage_key = ?",
                Integer.class, key)).isEqualTo(PDF.length);
        assertThat(Files.exists(Path.of(localDir).resolve(key))).isFalse();
        mvc.perform(get("/api/v1/claims/" + id + "/attachments/" + attachmentId).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF));
    }

    @Test
    void rejectedUploadsLeaveNoBytesAndTheTenFileLimitHolds() throws Exception {
        AppUser owner = fixtures.policyholder();
        UUID id = api.createDraft(owner, ClaimApi.validDraft(fixtures.activePolicy(owner)));
        int before = jdbc.queryForObject("select count(*) from attachment_blobs", Integer.class);
        upload(owner, id, "fake.pdf", "application/pdf", "not a pdf".getBytes())
                .andExpect(status().isUnsupportedMediaType());
        assertThat(jdbc.queryForObject("select count(*) from attachment_blobs", Integer.class)).isEqualTo(before);

        for (int i = 1; i <= 10; i++) {
            upload(owner, id, "page-" + i + ".pdf", "application/pdf", PDF).andExpect(status().isCreated());
        }
        upload(owner, id, "page-11.pdf", "application/pdf", PDF)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOO_MANY_FILES"));
        assertThat(blobCount(id)).isEqualTo(10);
    }

    @Test
    void bytesWithoutMetadataCannotBeCommitted() {
        String orphanKey = UUID.randomUUID().toString();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            try {
                storage.store(orphanKey, new ByteArrayInputStream(PDF), PDF.length);
            } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        })).isInstanceOfAny(DataAccessException.class, TransactionException.class);
        assertThat(jdbc.queryForObject("select count(*) from attachment_blobs where storage_key = ?",
                Integer.class, orphanKey)).isZero();
    }

    @Test
    void storedBytesRollBackWithTheirTransactionAndAreImmutable() throws Exception {
        AppUser owner = fixtures.policyholder();
        UUID id = api.createDraft(owner, ClaimApi.validDraft(fixtures.activePolicy(owner)));
        upload(owner, id, "photo.png", "image/png", PNG).andExpect(status().isCreated());
        String key = jdbc.queryForObject("select storage_key from claim_attachments where claim_id = ?",
                String.class, id);

        assertThatThrownBy(() -> jdbc.update("update attachment_blobs set content = ? where storage_key = ?",
                PDF, key)).isInstanceOf(DataAccessException.class);

        String rolledBack = UUID.randomUUID().toString();
        tx.executeWithoutResult(status -> {
            try {
                storage.store(rolledBack, new ByteArrayInputStream(PDF), PDF.length);
            } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select count(*) from attachment_blobs where storage_key = ?",
                Integer.class, rolledBack)).isZero();
        assertThatThrownBy(() -> storage.open(rolledBack)).isInstanceOf(java.io.FileNotFoundException.class);
    }
}

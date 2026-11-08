package com.claimwillow.attachment;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stores evidence bytes in the {@code attachment_blobs} table. Intended for hosts without a
 * persistent disk (for example a free container instance with a managed PostgreSQL database).
 * <p>
 * {@link #store} joins the caller's transaction, so the bytes commit or roll back together with the
 * attachment metadata. The foreign key to {@code claim_attachments} is checked at commit, which
 * means bytes can never be committed without their metadata row.
 */
public class DatabaseAttachmentStorage implements AttachmentStorage {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAttachmentStorage.class);
    private static final Pattern SAFE_KEY = Pattern.compile("^[a-f0-9-]{36}$");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate separateTransaction;
    private final long maxSizeBytes;

    public DatabaseAttachmentStorage(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
            long maxSizeBytes) {
        this.jdbc = jdbc;
        this.separateTransaction = new TransactionTemplate(transactionManager);
        this.separateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.maxSizeBytes = maxSizeBytes;
        log.info("Database attachment storage (attachment_blobs table)");
    }

    private static String checked(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return key;
    }

    @Override
    public void store(String key, InputStream content, long size) throws IOException {
        checked(key);
        if (size <= 0 || size > maxSizeBytes) {
            throw new IOException("Attachment size outside the allowed range");
        }
        byte[] bytes = content.readNBytes((int) size + 1);
        if (bytes.length != size) {
            throw new IOException("Attachment content does not match its declared size");
        }
        jdbc.update("INSERT INTO attachment_blobs (storage_key, content, size_bytes) VALUES (?, ?, ?)",
                key, bytes, (long) bytes.length);
    }

    @Override
    public InputStream open(String key) throws IOException {
        byte[] bytes = jdbc.query("SELECT content FROM attachment_blobs WHERE storage_key = ?",
                rs -> rs.next() ? rs.getBytes(1) : null, checked(key));
        if (bytes == null) {
            throw new FileNotFoundException("No stored content for attachment");
        }
        return new ByteArrayInputStream(bytes);
    }

    /**
     * Removes bytes in a transaction of its own. The upload flow only calls this after a rollback,
     * when the insert has already been undone, so it is a safety net rather than the main path.
     */
    @Override
    public void delete(String key) {
        String safeKey = checked(key);
        separateTransaction.executeWithoutResult(
                status -> jdbc.update("DELETE FROM attachment_blobs WHERE storage_key = ?", safeKey));
    }
}

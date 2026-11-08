package com.claimwillow.attachment;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class AttachmentConfig {

    /**
     * Selects where evidence bytes live (ATTACHMENT_STORAGE_MODE):
     * {@code local} (private directory on a persistent volume), {@code database} (attachment_blobs
     * table, for hosts without a persistent disk) or {@code azure} (placeholder that fails fast).
     */
    @Bean
    AttachmentStorage attachmentStorage(@Value("${app.attachments.storage-mode:local}") String mode,
            @Value("${app.attachments.local-dir:./data/attachments}") String localDir,
            @Value("${app.attachments.max-size-bytes:5242880}") long maxSizeBytes,
            @Value("${AZURE_STORAGE_ACCOUNT:}") String azureAccount,
            @Value("${AZURE_BLOB_CONTAINER:}") String azureContainer,
            ObjectProvider<JdbcTemplate> jdbc, ObjectProvider<PlatformTransactionManager> transactionManager)
            throws IOException {
        return switch (mode.trim().toLowerCase()) {
            case "local" -> new LocalFileSystemAttachmentStorage(Path.of(localDir));
            case "database" -> new DatabaseAttachmentStorage(jdbc.getObject(), transactionManager.getObject(),
                    maxSizeBytes);
            case "azure" -> new AzureBlobAttachmentStorage(azureAccount, azureContainer);
            default -> throw new IllegalStateException("Unknown ATTACHMENT_STORAGE_MODE: " + mode
                    + " (expected local, database or azure)");
        };
    }
}

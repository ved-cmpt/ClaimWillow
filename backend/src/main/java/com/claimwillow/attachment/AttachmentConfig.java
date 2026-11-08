package com.claimwillow.attachment;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AttachmentConfig {

    @Bean
    AttachmentStorage attachmentStorage(@Value("${app.attachments.storage-mode:local}") String mode,
            @Value("${app.attachments.local-dir:./data/attachments}") String localDir,
            @Value("${AZURE_STORAGE_ACCOUNT:}") String azureAccount,
            @Value("${AZURE_BLOB_CONTAINER:}") String azureContainer) throws IOException {
        return switch (mode.trim().toLowerCase()) {
            case "local" -> new LocalFileSystemAttachmentStorage(Path.of(localDir));
            case "azure" -> new AzureBlobAttachmentStorage(azureAccount, azureContainer);
            default -> throw new IllegalStateException("Unknown ATTACHMENT_STORAGE_MODE: " + mode);
        };
    }
}

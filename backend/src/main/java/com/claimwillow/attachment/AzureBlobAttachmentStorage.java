package com.claimwillow.attachment;

import java.io.InputStream;

/**
 * Placeholder for the hosted environment's private Azure Blob Storage container.
 * <p>
 * Not implemented yet: wiring it requires an Azure storage account, a private container and a
 * managed identity, which are provisioned outside this repository. Selecting
 * {@code ATTACHMENT_STORAGE_MODE=azure} fails fast at startup instead of silently storing files
 * somewhere unexpected. See docs/DEPLOYMENT.md.
 */
public class AzureBlobAttachmentStorage implements AttachmentStorage {

    private final String account;
    private final String container;

    public AzureBlobAttachmentStorage(String account, String container) {
        if (account == null || account.isBlank() || container == null || container.isBlank()) {
            throw new IllegalStateException(
                    "ATTACHMENT_STORAGE_MODE=azure requires AZURE_STORAGE_ACCOUNT and AZURE_BLOB_CONTAINER");
        }
        this.account = account;
        this.container = container;
        throw new IllegalStateException("Azure Blob attachment storage is not implemented in this build (account "
                + this.account + ", container " + this.container + "). Use ATTACHMENT_STORAGE_MODE=local with a "
                + "persistent volume, or implement this class with the Azure Storage SDK.");
    }

    @Override
    public void store(String key, InputStream content, long size) {
        throw new UnsupportedOperationException("Azure Blob storage is not implemented");
    }

    @Override
    public InputStream open(String key) {
        throw new UnsupportedOperationException("Azure Blob storage is not implemented");
    }

    @Override
    public void delete(String key) {
        throw new UnsupportedOperationException("Azure Blob storage is not implemented");
    }
}

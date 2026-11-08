package com.claimwillow.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores evidence on the local filesystem in a directory outside any static/public web path.
 * Used for development and single-node deployments with a persistent volume.
 */
public class LocalFileSystemAttachmentStorage implements AttachmentStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemAttachmentStorage.class);
    private static final Pattern SAFE_KEY = Pattern.compile("^[a-f0-9-]{36}$");

    private final Path root;

    public LocalFileSystemAttachmentStorage(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        log.info("Local attachment storage at {}", this.root);
    }

    private Path resolve(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }

    @Override
    public void store(String key, InputStream content, long size) throws IOException {
        Path target = resolve(key);
        Path temp = Files.createTempFile(root, "upload-", ".tmp");
        try {
            Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }
}

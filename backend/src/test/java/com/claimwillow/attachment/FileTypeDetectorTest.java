package com.claimwillow.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileTypeDetectorTest {

    @Test
    void detectsByMagicBytes() {
        assertThat(FileTypeDetector.detect("%PDF-1.7".getBytes())).contains("application/pdf");
        assertThat(FileTypeDetector.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}))
                .contains("image/png");
        assertThat(FileTypeDetector.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}))
                .contains("image/jpeg");
        assertThat(FileTypeDetector.detect("GIF89a".getBytes())).isEmpty();
        assertThat(FileTypeDetector.detect(new byte[]{'%', 'P'})).isEmpty();
        assertThat(FileTypeDetector.detect(null)).isEmpty();
    }

    @Test
    void extensionMustMatchContent() {
        assertThat(FileTypeDetector.extensionMatches("a.JPEG", "image/jpeg")).isTrue();
        assertThat(FileTypeDetector.extensionMatches("a.jpg", "image/jpeg")).isTrue();
        assertThat(FileTypeDetector.extensionMatches("a.png", "application/pdf")).isFalse();
        assertThat(FileTypeDetector.extensionMatches("noext", "application/pdf")).isFalse();
    }

    @Test
    void sanitizesFilenames() {
        assertThat(FileTypeDetector.sanitizeFilename("..\\..\\windows\\evil.pdf")).isEqualTo("evil.pdf");
        assertThat(FileTypeDetector.sanitizeFilename("a\"b<c>.png")).isEqualTo("a_b_c_.png");
        assertThat(FileTypeDetector.sanitizeFilename("")).isEqualTo("evidence");
        assertThat(FileTypeDetector.sanitizeFilename("x".repeat(300) + ".pdf")).hasSize(200).endsWith(".pdf");
    }

    @Test
    void localStorageRejectsKeysThatCouldEscapeTheRoot(@TempDir Path dir) throws Exception {
        LocalFileSystemAttachmentStorage storage = new LocalFileSystemAttachmentStorage(dir);
        String key = "0b8e5f8e-1c1a-4c1a-9f2e-3a3a3a3a3a3a";
        storage.store(key, new ByteArrayInputStream(new byte[]{1, 2, 3}), 3);
        assertThat(Files.readAllBytes(dir.resolve(key))).containsExactly(1, 2, 3);
        try (var in = storage.open(key)) {
            assertThat(in.readAllBytes()).hasSize(3);
        }
        assertThatThrownBy(() -> storage.open("../secret")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.store("a/b", new ByteArrayInputStream(new byte[0]), 0))
                .isInstanceOf(IllegalArgumentException.class);
        storage.delete(key);
        assertThat(Files.exists(dir.resolve(key))).isFalse();
    }

    @Test
    void azureModeFailsFastInsteadOfPretending() {
        assertThatThrownBy(() -> new AzureBlobAttachmentStorage("", "")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AZURE_STORAGE_ACCOUNT");
        assertThatThrownBy(() -> new AzureBlobAttachmentStorage("acct", "evidence"))
                .hasMessageContaining("not implemented");
    }
}

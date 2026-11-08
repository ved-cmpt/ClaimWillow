package com.claimwillow.attachment;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Identifies evidence files by their leading bytes (magic numbers), never by the client's
 * declared content type. Only PDF, PNG and JPEG are accepted.
 */
public final class FileTypeDetector {

    public static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg");

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private FileTypeDetector() {
    }

    public static Optional<String> detect(byte[] header) {
        if (startsWith(header, PDF)) {
            return Optional.of("application/pdf");
        }
        if (startsWith(header, PNG)) {
            return Optional.of("image/png");
        }
        if (startsWith(header, JPEG)) {
            return Optional.of("image/jpeg");
        }
        return Optional.empty();
    }

    /** The extension must be allowed and consistent with the detected type. */
    public static boolean extensionMatches(String filename, String detectedType) {
        String ext = extension(filename);
        return switch (detectedType) {
            case "application/pdf" -> ext.equals("pdf");
            case "image/png" -> ext.equals("png");
            case "image/jpeg" -> ext.equals("jpg") || ext.equals("jpeg");
            default -> false;
        };
    }

    public static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Strips any path, control characters and quotes; keeps at most 200 characters. */
    public static String sanitizeFilename(String original) {
        String name = original == null ? "" : original;
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|:*?]", "_").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            name = "evidence";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data != null && data.length >= prefix.length
                && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }
}

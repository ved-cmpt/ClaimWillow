package com.claimwillow.attachment;

import java.io.IOException;
import java.io.InputStream;

/**
 * Private storage for evidence bytes. Keys are random server-generated identifiers; they are never
 * returned to clients and never derived from user input.
 */
public interface AttachmentStorage {

    void store(String key, InputStream content, long size) throws IOException;

    InputStream open(String key) throws IOException;

    void delete(String key) throws IOException;
}

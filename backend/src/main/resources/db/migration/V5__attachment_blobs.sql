-- Evidence bytes kept inside PostgreSQL (ATTACHMENT_STORAGE_MODE=database), for hosts without a
-- persistent disk. Metadata stays in claim_attachments; this table only holds the content.

CREATE TABLE attachment_blobs (
    storage_key   VARCHAR(200) PRIMARY KEY,
    content       BYTEA        NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- The bytes are written just before their metadata row in the same transaction, so the
    -- reference is checked at commit. A blob can never outlive or exist without its metadata.
    CONSTRAINT fk_attachment_blobs_metadata FOREIGN KEY (storage_key)
        REFERENCES claim_attachments (storage_key) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_attachment_blobs_size CHECK (size_bytes > 0 AND size_bytes = octet_length(content))
);

-- PDF, PNG and JPEG are already compressed; store them out of line without recompressing.
ALTER TABLE attachment_blobs ALTER COLUMN content SET STORAGE EXTERNAL;

-- Evidence is immutable once stored, like the audit history.
CREATE TRIGGER trg_attachment_blobs_immutable
    BEFORE UPDATE ON attachment_blobs
    FOR EACH ROW EXECUTE FUNCTION forbid_append_only_mutation();

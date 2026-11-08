-- Notes written on a claim. STAFF_INTERNAL notes are never shown to policyholders.
CREATE TABLE claim_notes (
    id              UUID PRIMARY KEY,
    claim_id        UUID          NOT NULL REFERENCES claims (id),
    author_user_id  UUID          NOT NULL REFERENCES users (id),
    note_type       VARCHAR(30)   NOT NULL,
    body            VARCHAR(4000) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_notes_type CHECK (note_type IN
        ('STAFF_INTERNAL', 'REQUEST_INFO', 'POLICYHOLDER_RESPONSE', 'DECISION')),
    CONSTRAINT ck_notes_body CHECK (length(trim(body)) > 0)
);

CREATE INDEX ix_notes_claim ON claim_notes (claim_id, created_at);

CREATE TRIGGER trg_claim_notes_append_only
    BEFORE UPDATE OR DELETE ON claim_notes
    FOR EACH ROW EXECUTE FUNCTION forbid_append_only_mutation();

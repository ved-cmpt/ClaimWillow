-- Claims, rule findings, append-only audit events and attachment metadata.

CREATE TABLE claims (
    id                          UUID PRIMARY KEY,
    reference                   VARCHAR(20),
    policy_id                   UUID REFERENCES policies (id),
    owner_user_id               UUID          NOT NULL REFERENCES users (id),
    assigned_adjuster_user_id   UUID REFERENCES users (id),
    incident_date               DATE,
    incident_type               VARCHAR(20),
    title                       VARCHAR(120),
    description                 VARCHAR(4000),
    estimated_loss              NUMERIC(12, 2),
    currency                    VARCHAR(3)    NOT NULL DEFAULT 'CAD',
    status                      VARCHAR(20)   NOT NULL,
    -- Immutable policy snapshot captured at submission
    snapshot_policy_number      VARCHAR(32),
    snapshot_coverage_limit     NUMERIC(12, 2),
    snapshot_deductible         NUMERIC(12, 2),
    snapshot_effective_date     DATE,
    snapshot_expiration_date    DATE,
    snapshot_taken_at           TIMESTAMPTZ,
    last_validation_run_id      UUID,
    last_validated_at           TIMESTAMPTZ,
    version                     BIGINT        NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ   NOT NULL,
    updated_at                  TIMESTAMPTZ   NOT NULL,
    submitted_at                TIMESTAMPTZ,
    decided_at                  TIMESTAMPTZ,
    closed_at                   TIMESTAMPTZ,
    CONSTRAINT uq_claims_reference UNIQUE (reference),
    CONSTRAINT ck_claims_status CHECK (status IN
        ('DRAFT', 'SUBMITTED', 'IN_REVIEW', 'NEEDS_INFO', 'APPROVED', 'REJECTED', 'CLOSED')),
    CONSTRAINT ck_claims_incident_type CHECK (incident_type IS NULL OR incident_type IN ('DAMAGE', 'LOSS')),
    CONSTRAINT ck_claims_currency CHECK (currency = 'CAD'),
    CONSTRAINT ck_claims_estimate CHECK (estimated_loss IS NULL OR estimated_loss >= 0),
    CONSTRAINT ck_claims_submitted_complete CHECK (status = 'DRAFT' OR (
        reference IS NOT NULL AND policy_id IS NOT NULL AND incident_date IS NOT NULL
        AND incident_type IS NOT NULL AND title IS NOT NULL AND description IS NOT NULL
        AND estimated_loss IS NOT NULL AND estimated_loss >= 0.01
        AND snapshot_policy_number IS NOT NULL AND submitted_at IS NOT NULL))
);

CREATE INDEX ix_claims_owner ON claims (owner_user_id, created_at DESC);
CREATE INDEX ix_claims_status ON claims (status, submitted_at DESC);
CREATE INDEX ix_claims_assigned ON claims (assigned_adjuster_user_id);
CREATE INDEX ix_claims_created ON claims (created_at DESC);
CREATE INDEX ix_claims_duplicate_lookup ON claims (owner_user_id, policy_id, incident_type, incident_date);

CREATE TABLE claim_findings (
    id                  UUID PRIMARY KEY,
    claim_id            UUID          NOT NULL REFERENCES claims (id),
    run_id              UUID          NOT NULL,
    rule_code           VARCHAR(40)   NOT NULL,
    rule_version        INTEGER       NOT NULL,
    severity            VARCHAR(10)   NOT NULL,
    explanation         VARCHAR(1000) NOT NULL,
    related_claim_id    UUID REFERENCES claims (id),
    checked_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_findings_severity CHECK (severity IN ('INFO', 'WARNING'))
);

CREATE INDEX ix_findings_claim ON claim_findings (claim_id, checked_at DESC);

CREATE TABLE claim_events (
    id              UUID PRIMARY KEY,
    seq             BIGINT GENERATED ALWAYS AS IDENTITY,
    claim_id        UUID          NOT NULL REFERENCES claims (id),
    actor_user_id   UUID          NOT NULL REFERENCES users (id),
    event_type      VARCHAR(40)   NOT NULL,
    from_status     VARCHAR(20),
    to_status       VARCHAR(20),
    summary         VARCHAR(500)  NOT NULL,
    visibility      VARCHAR(10)   NOT NULL DEFAULT 'ALL',
    occurred_at     TIMESTAMPTZ   NOT NULL,
    metadata_json   TEXT,
    CONSTRAINT uq_claim_events_seq UNIQUE (seq),
    CONSTRAINT ck_events_visibility CHECK (visibility IN ('ALL', 'STAFF'))
);

CREATE INDEX ix_events_claim ON claim_events (claim_id, seq);

CREATE TABLE claim_attachments (
    id                      UUID PRIMARY KEY,
    claim_id                UUID          NOT NULL REFERENCES claims (id),
    uploaded_by_user_id     UUID          NOT NULL REFERENCES users (id),
    original_filename       VARCHAR(255)  NOT NULL,
    detected_content_type   VARCHAR(100)  NOT NULL,
    storage_key             VARCHAR(200)  NOT NULL,
    size_bytes              BIGINT        NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_attachments_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_attachments_size CHECK (size_bytes > 0)
);

CREATE INDEX ix_attachments_claim ON claim_attachments (claim_id, created_at);

-- Audit history and rule findings are append-only at the database level too.
CREATE FUNCTION forbid_append_only_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'table % is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_claim_events_append_only
    BEFORE UPDATE OR DELETE ON claim_events
    FOR EACH ROW EXECUTE FUNCTION forbid_append_only_mutation();

CREATE TRIGGER trg_claim_findings_append_only
    BEFORE UPDATE OR DELETE ON claim_findings
    FOR EACH ROW EXECUTE FUNCTION forbid_append_only_mutation();

-- Once captured, the policy snapshot on a claim can never change.
CREATE FUNCTION forbid_snapshot_change() RETURNS trigger AS $$
BEGIN
    IF OLD.snapshot_policy_number IS NOT NULL AND (
        NEW.snapshot_policy_number IS DISTINCT FROM OLD.snapshot_policy_number
        OR NEW.snapshot_coverage_limit IS DISTINCT FROM OLD.snapshot_coverage_limit
        OR NEW.snapshot_deductible IS DISTINCT FROM OLD.snapshot_deductible
        OR NEW.snapshot_effective_date IS DISTINCT FROM OLD.snapshot_effective_date
        OR NEW.snapshot_expiration_date IS DISTINCT FROM OLD.snapshot_expiration_date) THEN
        RAISE EXCEPTION 'policy snapshot on claim % is immutable', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_claims_snapshot_immutable
    BEFORE UPDATE ON claims
    FOR EACH ROW EXECUTE FUNCTION forbid_snapshot_change();

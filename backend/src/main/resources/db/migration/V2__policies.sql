-- Home-contents policies held by policyholders (synthetic data only).
CREATE TABLE policies (
    id                      UUID PRIMARY KEY,
    policy_number           VARCHAR(32)   NOT NULL,
    policyholder_user_id    UUID          NOT NULL REFERENCES users (id),
    status                  VARCHAR(20)   NOT NULL,
    product_name            VARCHAR(80)   NOT NULL,
    insured_address         VARCHAR(200)  NOT NULL,
    effective_date          DATE          NOT NULL,
    expiration_date         DATE          NOT NULL,
    coverage_limit          NUMERIC(12, 2) NOT NULL,
    deductible              NUMERIC(12, 2) NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_policies_number UNIQUE (policy_number),
    CONSTRAINT ck_policies_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_policies_dates CHECK (expiration_date >= effective_date),
    CONSTRAINT ck_policies_amounts CHECK (coverage_limit > 0 AND deductible >= 0)
);

CREATE INDEX ix_policies_policyholder ON policies (policyholder_user_id);

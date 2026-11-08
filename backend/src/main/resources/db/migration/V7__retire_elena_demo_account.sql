-- The synthetic demo now has exactly five accounts. The former demo policyholder
-- elena@example.com (synthetic, never a real person) is folded into marcus@example.com:
-- her policies, claims, notes, evidence and audit entries move to Marcus, then the account is removed.
-- This is a one-off data correction for synthetic demo data only; it does nothing on a database
-- without both demo accounts. The append-only guards are paused only for these rows, inside this
-- migration's transaction, and restored before it commits.
DO $$
DECLARE
    retired UUID;
    target  UUID;
BEGIN
    SELECT id INTO retired FROM users WHERE email = 'elena@example.com';
    SELECT id INTO target FROM users WHERE email = 'marcus@example.com';
    IF retired IS NULL OR target IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE claim_events DISABLE TRIGGER trg_claim_events_append_only;
    ALTER TABLE claim_notes DISABLE TRIGGER trg_claim_notes_append_only;

    UPDATE policies SET policyholder_user_id = target WHERE policyholder_user_id = retired;
    UPDATE claims SET owner_user_id = target WHERE owner_user_id = retired;
    UPDATE claims SET assigned_adjuster_user_id = NULL WHERE assigned_adjuster_user_id = retired;
    UPDATE claim_events SET actor_user_id = target WHERE actor_user_id = retired;
    UPDATE claim_notes SET author_user_id = target WHERE author_user_id = retired;
    UPDATE claim_attachments SET uploaded_by_user_id = target WHERE uploaded_by_user_id = retired;

    ALTER TABLE claim_events ENABLE TRIGGER trg_claim_events_append_only;
    ALTER TABLE claim_notes ENABLE TRIGGER trg_claim_notes_append_only;

    DELETE FROM users WHERE id = retired;
END
$$;

-- Sign-in by username. Email stays on the account as a profile field.
-- Usernames are stored trimmed and lower case, so the unique constraint is case-insensitive.
ALTER TABLE users ADD COLUMN username VARCHAR(40);

-- Backfill every existing row so this never fails on an existing database.
-- The synthetic demo accounts get their published short names; any other account gets the
-- local part of its email (reduced to the allowed characters). Clashes get a short id suffix.
WITH candidates AS (
    SELECT id,
           created_at,
           CASE email
               WHEN 'priya@example.com' THEN 'priya'
               WHEN 'marcus@example.com' THEN 'marcus'
               WHEN 'elena@example.com' THEN 'elena'
               WHEN 'dana.adjuster@example.com' THEN 'dana'
               WHEN 'sam.adjuster@example.com' THEN 'sam'
               WHEN 'admin@example.com' THEN 'admin'
           END AS demo_name,
           left(regexp_replace(regexp_replace(lower(split_part(email, '@', 1)), '[^a-z0-9._-]', '-', 'g'),
                               '^[^a-z0-9]+', ''), 24) AS local_name
    FROM users
),
named AS (
    SELECT id,
           demo_name IS NOT NULL AS is_demo,
           created_at,
           CASE WHEN length(coalesce(demo_name, local_name)) >= 3 THEN coalesce(demo_name, local_name)
                ELSE 'user-' || coalesce(demo_name, local_name) END AS base
    FROM candidates
),
ranked AS (
    SELECT id, base,
           row_number() OVER (PARTITION BY base ORDER BY is_demo DESC, created_at, id) AS n
    FROM named
)
UPDATE users u
SET username = CASE WHEN r.n = 1 THEN r.base ELSE r.base || '-' || left(replace(u.id::text, '-', ''), 8) END
FROM ranked r
WHERE r.id = u.id;

ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_username UNIQUE (username);
ALTER TABLE users ADD CONSTRAINT ck_users_username_format
    CHECK (username = lower(username) AND username ~ '^[a-z0-9][a-z0-9._-]{2,39}$');

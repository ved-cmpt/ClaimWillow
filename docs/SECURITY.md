# Security and privacy

ClaimWillow is a portfolio application. **Use synthetic data only.** Do not upload real insurance documents,
identity documents or any personal information. Nothing here implies compliance with any insurance, privacy or
security regulation.

## Authentication and sessions

- Server-side sessions (Spring Security). Cookie `CLAIMWILLOW_SESSION`: `HttpOnly`, `SameSite=Lax`, `Secure` in the
  `prod` profile (`SESSION_COOKIE_SECURE`), 30-minute idle timeout.
- Behind a TLS-terminating proxy (for example Render), `X-Forwarded-Proto` is honoured, so the app treats requests as
  HTTPS: the session and CSRF cookies are marked `Secure` and HSTS is sent. `ProxiedHttpsIT` checks this over real
  HTTP, including sign-in with the CSRF header.
- Sign-in via `POST /api/v1/auth/login`; the session id changes at sign-in (fixation protection) and the CSRF token
  rotates. Sign-out invalidates the session.
- Passwords hashed with BCrypt (cost 12). No plaintext or default production passwords in the repository.
- Sign-in throttling: 5 failed attempts per account (30 per client address) within 15 minutes → HTTP 429. Same
  generic error for unknown accounts, wrong passwords and disabled accounts.
- Demo accounts are created only when `APP_SEED_DEMO_DATA=true`, with the password from `APP_SEED_DEMO_PASSWORD`
  Seeding is skipped, with a log warning, unless the password has at least 12 characters, mixes at least three of
  lowercase, uppercase, digits and symbols, and is not the dev password documented in the README. The `local`
  development profile and the local Docker stack may use that documented dev-only default
  (`APP_SEED_DEMO_ALLOW_DOCUMENTED_PASSWORD`) so the project runs out of the box. A hosted `prod` deployment cannot
  use it.

## CSRF

Cookie-to-header pattern for the same-origin SPA: `CookieCsrfTokenRepository.withHttpOnlyFalse()` issues
`XSRF-TOKEN`; the client echoes it in `X-XSRF-TOKEN` on every POST/PATCH (including uploads). A custom request handler
accepts the raw header value from the SPA while keeping BREACH-safe tokens for any form fields. `AuthIT` proves a
state-changing request fails without the token, fails with a wrong token, and succeeds with the right one.

## Authorization matrix

All checks are enforced on the server; the UI only hides what the server would refuse. "404" means the resource is
reported as missing so its existence is not revealed.

| Action | Policyholder (owner) | Other policyholder | Adjuster | Admin | Anonymous |
| --- | --- | --- | --- | --- | --- |
| View own policies | ✓ | — | 403 (`/policies/mine`) | 403 | 401 |
| View a policy by id | ✓ own | 404 | ✓ | ✓ | 401 |
| Create / edit draft, submit | ✓ (DRAFT only) | 404 | 403 | 403 | 401 |
| View draft claim | ✓ | 404 | 404 | 404 | 401 |
| View submitted claim, findings, timeline | ✓ | 404 | ✓ | ✓ | 401 |
| View notes | request, response and decision notes | 404 | all incl. internal | all | 401 |
| Respond to information request | ✓ (NEEDS_INFO only) | 404 | 403 | 403 | 401 |
| Upload evidence | ✓ (DRAFT, NEEDS_INFO) | 404 | ✓ (open claims) | ✓ (open claims) | 401 |
| Download evidence | ✓ | 404 | ✓ (non-draft) | ✓ (non-draft) | 401 |
| Work queue, dashboard, reports, CSV | 403 | 403 | ✓ | ✓ | 401 |
| Assign | 403 | 403 | to self, if unassigned | anyone, reassign | 401 |
| Start review, request info, decide | 403 | 403 | assigned adjuster only | assigned only | 401 |
| Close decided claim | 403 | 403 | assigned adjuster | ✓ | 401 |
| Internal notes | 403 | 403 | ✓ | ✓ | 401 |
| Account list, enable/disable | 403 | 403 | 403 | ✓ (not self) | 401 |
| `/actuator/health` | ✓ | ✓ | ✓ | ✓ | ✓ (status only) |
| Other actuator endpoints | denied | denied | denied | denied | denied |

Never trusted from the browser: owner id, role, assignee (except an admin's explicit choice), policy snapshot,
findings, references, status. All are derived on the server.

## Evidence files

- Allowed: PDF, PNG, JPEG — identified by **magic bytes**; the declared content type is ignored and the extension must
  match the detected type.
- Limits: 5 MB per file (HTTP 413), 10 files per claim, empty files rejected.
- Stored under a random UUID key, either in a directory outside any static/web path (`local`) or in the
  `attachment_blobs` table (`database`, used for free hosting). Database rows can only be committed together with
  their metadata row (deferred foreign key) and cannot be updated afterwards (trigger).
  Original filenames are sanitised (path and control characters removed) and only used for display/download names.
  Storage keys never appear in API responses.
- Downloads are streamed only after the same visibility check as the claim, with `Content-Disposition: attachment`,
  `X-Content-Type-Options: nosniff`, `Cache-Control: private, no-store` and a sandboxing CSP.
- If the database transaction fails, the stored bytes are deleted (local mode) or rolled back with it (database
  mode).
- **No malware scanning** is performed. Until scanning and further controls exist, evidence must be synthetic test
  files only.

## Data integrity

- Status transitions validated in one place; illegal transitions are 409.
- Optimistic locking (`version`) on claims; staff actions and draft saves must send the version they saw.
- Every state-changing claim operation first takes a row lock on the claim (`SELECT ... FOR UPDATE`), so concurrent
  writers are serialised and the losers get 409 instead of a database deadlock. Covered by a test that races six
  adjusters 20 times.
- Audit events are written in the same transaction as the change they describe (`AuditService` requires an existing
  transaction). Events, findings and notes are append-only, enforced by database triggers as well as code.
- Policy snapshots are immutable once captured (database trigger).
- Money in `NUMERIC(12,2)`/`BigDecimal`; rule comparisons use `compareTo`, never floating point.

## Input handling and headers

- Bean Validation on every request body plus business validation in services; malformed input is 400 with field
  errors; unexpected errors are logged server-side and returned as a generic message with a request id.
- Only parameterised JPA/Criteria queries (no string-built SQL). The staff queue search escapes LIKE wildcards; the
  policyholder search is already scoped to the caller's own claims.
- CSV export neutralises spreadsheet formulas (cells starting with `= + - @` get a leading quote) and uses RFC 4180
  quoting.
- Headers: `Content-Security-Policy` (`default-src 'self'`, no inline scripts, `frame-ancestors 'none'`),
  `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`, no-store caching for API
  responses.

## Logging

One access line per API request (method, path, status, duration) and application events, with a request id in every
line and in the `X-Request-Id` response header. JSON (ECS) format in `prod`. Never logged: passwords, session or CSRF
cookies, request bodies, file contents.

## Repository hygiene

- `.env` files, dumps, uploaded evidence and build output are git-ignored; `.env.example` holds placeholders only.
- Local Docker credentials in `docker-compose.yml` are dev-only defaults bound to `127.0.0.1`.
- Dependabot configuration is included; enable GitHub secret scanning and Dependabot alerts on the repository.

## Threats considered

| Threat | Mitigation |
| --- | --- |
| Broken object-level authorization (guessing claim ids) | UUIDs plus per-object owner/staff checks; 404 for invisible resources; tests for every claim endpoint |
| CSRF | Token required on all state changes; SameSite=Lax cookie |
| Session fixation / theft | Session id rotation at login; HttpOnly; Secure in production; no tokens in URLs |
| Credential stuffing / brute force | BCrypt, throttling with 429, generic errors |
| Lost updates / race on assignment | Version checks + `@Version`; concurrent assignment test (exactly one winner) |
| Malicious uploads (polyglots, scripts, path traversal) | Magic-byte allow-list, extension match, size limits, random keys, no web-root storage, attachment disposition, nosniff, sandbox CSP |
| Stored XSS | React escapes all rendered text; strict CSP without inline scripts |
| CSV/formula injection | Formula neutralisation in the export |
| Tampering with history | Append-only tables enforced by triggers; events in the same transaction |
| Information leakage in errors | Uniform error body without stack traces; detailed causes only in server logs |

## Known limitations

- No malware scanning of uploads; synthetic files only.
- Sessions and the login throttle are in memory: a restart signs everyone out, and running more than one replica
  needs sticky sessions or a shared session store first.
- Disabling an account blocks new sign-ins; an existing session continues until it expires or signs out.
- No multi-factor authentication, password reset or self-service registration (demo accounts are provisioned).
- Azure Blob storage for evidence is a placeholder. Hosted deployments use `database` mode or a private mounted
  volume.
- No formal penetration test or compliance assessment has been performed.

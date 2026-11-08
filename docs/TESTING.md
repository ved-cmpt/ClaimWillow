# Testing

ClaimWillow is tested at four layers. Each one runs locally with the commands below and in GitHub Actions
(`.github/workflows/ci.yml`). Nothing is mocked at the database level: integration and browser tests run against a
real PostgreSQL started by Testcontainers, never the developer database.

| Layer | Tools | Location | Command |
| --- | --- | --- | --- |
| Backend unit | JUnit Jupiter 6, AssertJ, Mockito | `backend/src/test/java/**/*Test.java` | `cd backend; ./mvnw test` |
| Backend integration | Spring Boot Test, MockMvc, Testcontainers PostgreSQL 16 | `backend/src/test/java/**/*IT.java` | `cd backend; ./mvnw verify` |
| Frontend | Vitest, React Testing Library, user-event, jsdom | `frontend/src/tests/` | `cd frontend; npm test -- --run` |
| Browser acceptance | Cucumber 7 (Gherkin), Selenium 4, headless Chrome, Testcontainers | `e2e/` | `cd e2e; ./mvnw test` |

On Windows use `.\mvnw.cmd` instead of `./mvnw`. Docker must be running for integration and browser tests.

## Backend unit tests (fast, no Spring context)

- `ClaimRulesTest` — R1 required inputs, R2 policy-period boundaries (inclusive on both ends), R3/R4 with
  `BigDecimal` scale differences, R5 duplicate window and tolerance (and that another user's claim is never linked),
  R6 completeness.
- `ClaimStateMachineTest` — every legal transition, a matrix of illegal ones (all 409), no self transitions, final
  decisions.
- `ClaimActionTest` — which actions each role sees for each status.
- `FileTypeDetectorTest` — magic-byte detection, extension matching, filename sanitising, storage path-traversal
  guard, fail-fast Azure placeholder.
- `LoginAttemptServiceTest` — sign-in throttling window with a controllable clock.
- `DemoPasswordPolicyTest`: weak, short or documented-dev demo passwords are refused for hosted seeding.

## Backend integration tests (real PostgreSQL)

The `@IntegrationTest` meta-annotation starts the full application against a disposable `postgres:16-alpine`
container (Spring Boot `@ServiceConnection`). Flyway applies every migration; Hibernate validates the schema. Each
test creates its own users and policies with random identifiers (`TestFixtures`), so tests do not depend on order.

- `SystemStatusIT` — migrations applied, health endpoint public, other actuator endpoints closed, 401 JSON.
- `AuthIT` — real cookie + `X-XSRF-TOKEN` flow: login fails without the token and succeeds with it, the token rotates
  at login, logout requires the token, generic error for bad credentials, disabled accounts, rate limiting (429).
- `PolicyAuthorizationIT` — owner-only policies, 404 for other owners, staff read access, staff cannot use claimant
  endpoints, 401/403 boundaries.
- `ClaimIntakeIT` — draft → reopen → submit persists the snapshot, findings and events; later policy changes do not
  rewrite the snapshot; audit/findings tables reject UPDATE/DELETE; incomplete or invalid input is rejected without
  changing state; resubmission is 409; stale draft version is 409; ownership boundaries on every claim endpoint;
  out-of-period and duplicate findings; pagination, filters and counts.
- `ReviewLifecycleIT` — the full two-role journey to CLOSED with ten timeline events; illegal transitions; stale
  version and second-adjuster conflicts; **six threads assigning the same claim at once produce exactly one winner
  and one ASSIGNED event, repeated 20 times** (this caught a lock-ordering deadlock on CI, fixed by locking the
  claim row first); internal notes are staff-only; staff endpoints are role protected; queue filters/paging.
- `DecisionRollbackIT` — a temporary CHECK constraint makes PostgreSQL reject the decision's audit event; the claim
  status, decision timestamp, version and decision note are all unchanged afterwards (one transaction).
- `AttachmentIT` — owner upload, download headers, 404 for other owners and for staff on drafts, 401 anonymous,
  attachment ids scoped to their claim, type rejection by content (declared content type is ignored), size limit
  413, status rules, CSRF required for uploads, evidence counts as a response to an information request.
- `DatabaseAttachmentStorageIT`: runs every `AttachmentIT` scenario again with `ATTACHMENT_STORAGE_MODE=database`.
  It also checks that the bytes land in `attachment_blobs` and not on disk, that rejected uploads leave no bytes,
  that the 10-file limit holds, that bytes without a metadata row cannot be committed (deferred foreign key), that
  bytes roll back with their transaction, and that stored evidence cannot be updated.
- `ProxiedHttpsIT`: real HTTP to the embedded server with `X-Forwarded-Proto: https`, as a hosting proxy sends it.
  The CSRF and session cookies are `Secure`, HSTS is sent, sign-in without the CSRF header is refused and with it
  succeeds, and the session works afterwards.
- `ReportIT` — CSV contents match the database, drafts excluded, formula-injection neutralised, role checks; admin
  account management boundaries.
- `DemoSeedIT` — the synthetic seed is idempotent and covers several statuses.

## Frontend tests

Vitest + React Testing Library with `fetch` stubbed per test (`tests/testUtils.tsx`):

- `LoginPage` — required fields, server error message, CSRF header sent, role-based redirect.
- `ProtectedRoute` — anonymous redirect, wrong-role redirect, role navigation.
- `ClaimForm` — R1 mirror validation, a full save → review → submit flow (request bodies asserted), server field
  errors mapped back onto inputs.
- `StaffReviewPanel` — only server-allowed actions render, required reasons, decision confirmation with the version the
  user saw, 409 conflict message with refresh; owner response card visibility.
- `Evidence` — client prechecks, server 415 message shown, reports page export link.
- `ClaimStatusPill`, formatters, API client helpers.

## Browser acceptance tests (Cucumber + Selenium)

Feature files live in `e2e/src/test/resources/features/` and cover the ten scenarios from the specification:

| # | Scenario | Feature file |
| --- | --- | --- |
| 1 | Policyholder signs in, saves a draft (survives reload) and submits | `policyholder_claims.feature` |
| 2 | Validation warnings (R3, R6) and submitted status are shown | `policyholder_claims.feature` |
| 3 | Adjuster finds an unassigned claim in the queue, assigns it and starts review | `adjuster_review.feature` |
| 4 | Request for information requires a reason | `adjuster_review.feature` |
| 5 | Policyholder answers; staff see the response, history and decision panel | `information_request.feature` |
| 6 | Approval requires a note; claim is approved then closed | `adjuster_review.feature` |
| 7 | A policyholder cannot open another policyholder's claim | `access_control.feature` |
| 8 | Anonymous visitors are redirected to sign in, then returned | `access_control.feature` |
| 9 | Two adjusters in separate browsers: the stale one gets a conflict, nothing is overwritten | `adjuster_review.feature` |
| 10 | A file with an image extension but text contents is rejected with a visible message | `policyholder_claims.feature` |

How the suite runs (`e2e/src/test/java/com/claimwillow/e2e/support/AppUnderTest.java`):

1. Starts a disposable PostgreSQL container (Testcontainers).
2. Launches the real `backend/target/claimwillow.jar` on a free port, serving `frontend/dist`, with synthetic demo
   accounts seeded and a test-only password.
3. Each scenario prepares its own data through the public API (`ApiClient`, real session + CSRF) with a unique tag,
   so scenarios are independent and can run in any order.
4. Each actor gets an isolated headless Chrome (Selenium Manager resolves the driver). Selectors are `data-testid`
   attributes; waits are explicit `WebDriverWait` conditions — no fixed sleeps in steps.
5. A failed scenario saves a PNG per open browser to `e2e/target/screenshots/` and attaches it to
   `e2e/target/cucumber-report.html`. The app log is in `e2e/target/e2e-backend.log`.

Prerequisites: build the jar and the frontend first.

```bash
cd frontend && npm ci && npm run build && cd ..
cd backend && ./mvnw -DskipTests package && cd ..
cd e2e && ./mvnw test
```

Options: `E2E_HEADLESS=false` shows the browsers; `E2E_BASE_URL=http://localhost:8080` runs against an already
running instance (it must have been seeded with `APP_SEED_DEMO_PASSWORD` equal to `E2E_DEMO_PASSWORD`).

One date input is filled through the browser's native value setter plus an `input` event, because typing into
`<input type="date">` depends on the OS locale; every other interaction is a real click or keystroke.

## CI

`ci.yml` runs on pushes to `main`/`phase-*` and on pull requests:

1. **backend** — `./mvnw -B verify` (unit + integration); uploads surefire/failsafe reports.
2. **frontend** — `npm ci`, `npm run lint`, `npm run test -- --run`, `npm run build`.
3. **e2e** — builds the frontend and jar, runs the Cucumber suite, uploads the HTML report, screenshots and app log.
4. **docker**: builds the production image and smoke-tests it against a throwaway database with the free-hosting
   settings (`--memory=512m`, `ATTACHMENT_STORAGE_MODE=database`): health and readiness UP, SPA served, memory
   printed. Startup time and memory at 512 MB, measured locally, are in
   [DEPLOYMENT.md section 3.8](DEPLOYMENT.md#38-sizing-and-troubleshooting).

A failing job fails the workflow; no step is marked "continue on error".

## Latest local results

Recorded from actual runs on the development machine (Windows 11, Java 21, Node 24, Docker Desktop):

| Layer | Result |
| --- | --- |
| Backend unit | 75 passed, 0 failed |
| Backend integration | 56 passed, 0 failed (including database-mode attachments and the HTTPS proxy check) |
| Frontend | 29 passed, 0 failed |
| Browser acceptance | 10 scenarios / 96 steps passed |

The same four CI jobs (backend, frontend, e2e, docker) run on GitHub Actions for every push and pull request; see
the repository's Actions tab for the latest runs.

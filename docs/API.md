# API

Base path: `/api/v1`. JSON in and out. The OpenAPI document is served at `/v3/api-docs` and Swagger UI at
`/swagger-ui.html` (enabled in development; disabled in the `prod` profile unless `APP_SWAGGER_UI_ENABLED=true`).

## Authentication model

- Server-side session (cookie `CLAIMWILLOW_SESSION`, HttpOnly, SameSite=Lax, Secure in production).
- CSRF protection on every state-changing request. The server issues an `XSRF-TOKEN` cookie (readable by the SPA);
  the client echoes it in the `X-XSRF-TOKEN` header. The token rotates at sign-in.
- `GET /auth/csrf` makes sure the cookie exists before the first POST.
- Login is rate limited: 5 failures per account (30 per client address) within 15 minutes → HTTP 429.

```bash
# 1. get the CSRF cookie
curl -c jar -b jar http://localhost:8080/api/v1/auth/csrf
# 2. sign in (token value copied from the XSRF-TOKEN cookie in "jar")
curl -c jar -b jar -H "X-XSRF-TOKEN: <token>" -H "Content-Type: application/json" \
     -d '{"email":"priya@example.com","password":"<demo password>"}' http://localhost:8080/api/v1/auth/login
```

## Errors

Every error uses one shape:

```json
{
  "timestamp": "2026-11-08T08:40:26.853Z",
  "status": 409,
  "code": "STALE_CLAIM",
  "message": "This claim was changed by someone else since you opened it. Refresh to see the latest version before trying again.",
  "path": "/api/v1/staff/claims/a2f6.../assign",
  "requestId": "32dead62-4977-47cb-ba0b-7d02f0e43fa1",
  "fieldErrors": [{ "field": "estimatedLoss", "message": "Use at most two decimal places." }],
  "details": { "currentVersion": 2, "yourVersion": 1, "currentStatus": "SUBMITTED" }
}
```

| Status | When |
| --- | --- |
| 400 | Malformed JSON or parameters, bean validation, rule R1 (`VALIDATION_FAILED` with `fieldErrors`) |
| 401 | Not signed in (`UNAUTHENTICATED`) |
| 403 | Signed in but wrong role, or missing/invalid CSRF token |
| 404 | Missing **or not visible to you** (another owner's claim, someone's draft) — existence is not leaked |
| 409 | `INVALID_TRANSITION`, `STALE_CLAIM`, `ALREADY_ASSIGNED`, `NOT_ASSIGNEE`, `NOT_A_DRAFT`, `CLAIM_CLOSED` |
| 413 | Upload larger than 5 MB |
| 415 | Unsupported content type or file type |
| 429 | Too many failed sign-in attempts |

Every response carries `X-Request-Id`; the same id appears in server logs.

## Endpoints

### Authentication
| Method | Path | Who | Notes |
| --- | --- | --- | --- |
| GET | `/auth/csrf` | anyone | issues `XSRF-TOKEN` cookie |
| POST | `/auth/login` | anyone | `{email, password}` → current user |
| POST | `/auth/logout` | anyone | invalidates the session, 204 |
| GET | `/auth/me` | signed in | `{id, email, displayName, role}` |

### Policies
| GET | `/policies/mine` | policyholder | own policies |
| --- | --- | --- | --- |
| GET | `/policies/{id}` | owner or staff | 404 otherwise |

### Claims (policyholder and shared views)
| Method | Path | Who | Notes |
| --- | --- | --- | --- |
| GET | `/claims/mine?status=&query=&page=&size=` | policyholder | paginated, newest activity first |
| GET | `/claims/mine/summary` | policyholder | counts by status |
| POST | `/claims` | policyholder | create DRAFT (all fields optional) → 201 |
| GET | `/claims/{id}` | owner; staff for non-drafts | includes `version`, `availableActions`, `openRequest` |
| PATCH | `/claims/{id}/draft` | owner, DRAFT only | `{policyId, incidentDate, incidentType, title, description, estimatedLoss, version}` |
| POST | `/claims/{id}/submit` | owner, DRAFT only | `{version}`; R1, snapshot, R2–R6 |
| POST | `/claims/{id}/responses` | owner, NEEDS_INFO only | `{version, message}` |
| GET | `/claims/{id}/findings` | owner or staff | latest validation run |
| GET | `/claims/{id}/timeline` | owner or staff | staff-only events hidden from owners |
| GET | `/claims/{id}/notes` | owner or staff | internal notes are staff-only |
| GET | `/rules` | signed in | rule catalog |

### Staff (ADJUSTER or ADMIN)
| Method | Path | Body |
| --- | --- | --- |
| GET | `/staff/claims?status=&status=&assignedTo=me\|unassigned\|{userId}&query=&submittedFrom=&submittedTo=&sort=&page=&size=` | — |
| GET | `/staff/dashboard/summary` | — |
| GET | `/staff/adjusters` | — |
| POST | `/staff/claims/{id}/assign` | `{version, adjusterId?}` (adjusterId: admin only) |
| POST | `/staff/claims/{id}/start-review` | `{version}` |
| POST | `/staff/claims/{id}/request-info` | `{version, reason}` |
| POST | `/staff/claims/{id}/decision` | `{version, decision: "APPROVED"\|"REJECTED", reason}` |
| POST | `/staff/claims/{id}/close` | `{version, note?}` |
| POST | `/staff/claims/{id}/notes` | `{body}` (internal note) |

Sort values: `submittedAt,desc` (default), `submittedAt,asc`, `updatedAt,desc`, `estimatedLoss,desc`.

### Evidence
| Method | Path | Who | Notes |
| --- | --- | --- | --- |
| GET | `/claims/{id}/attachments` | owner or staff | metadata only (`id, filename, contentType, sizeBytes, uploadedBy, createdAt, downloadUrl`); storage keys are never returned |
| POST | `/claims/{id}/attachments` | owner (DRAFT, NEEDS_INFO); staff (open claims) | `multipart/form-data`, field `file`; PDF/PNG/JPEG by magic bytes, 5 MB, 10 per claim; 413/415/409 |
| GET | `/claims/{id}/attachments/{attachmentId}` | owner or staff (non-draft) | streamed with `Content-Disposition: attachment`, `nosniff` |

### Reports (ADJUSTER or ADMIN)
| Method | Path | Notes |
| --- | --- | --- |
| GET | `/staff/reports/summary?submittedFrom=&submittedTo=` | counts and estimated totals per status (drafts excluded) |
| GET | `/staff/reports/claims.csv?status=&submittedFrom=&submittedTo=` | CSV, newest first, max 10,000 rows, formula-safe |

### Admin (ADMIN)
| Method | Path | Notes |
| --- | --- | --- |
| GET | `/admin/users` | all accounts (no password hashes) |
| PATCH | `/admin/users/{id}` | `{enabled: true\|false}`; not your own account |

### Decision example

```http
POST /api/v1/staff/claims/{id}/decision
X-XSRF-TOKEN: <token>
Content-Type: application/json

{"version": 5, "decision": "APPROVED", "reason": "Reviewed photos and policy terms."}
```

### Operations
| GET | `/actuator/health` | anyone | `{"status":"UP"}` only; other actuator endpoints are denied |
| --- | --- | --- | --- |
| GET | `/api/v1/system/status` | anyone | name, status, server time |

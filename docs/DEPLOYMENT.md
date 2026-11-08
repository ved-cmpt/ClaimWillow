# Deployment

ClaimWillow ships as **one container image** plus **one PostgreSQL database**. The image contains the Vite-built
React app, served by Spring Boot, with the API under `/api/v1`. Evidence files can live in that same database
(`ATTACHMENT_STORAGE_MODE=database`), so a host does not need a persistent disk.

This guide is written for the repository owner and has three paths:

- **Section 3, Free hosting: Render + Neon.** This is the primary path and the **live deployment**. A free Render
  web service runs the Docker image and a free Neon project provides PostgreSQL. `render.yaml` at the repository
  root describes the service.
- **Section 4, Azure Container Apps.** The alternative for a paid, always-on deployment.
- **Section 5, Other hosts.** Any container host with HTTPS and a managed PostgreSQL database.

**Live deployment:** https://claimwillow.onrender.com (Render free web service `claimwillow`, Neon free PostgreSQL,
`ATTACHMENT_STORAGE_MODE=database`). It holds synthetic data only, and its demo password is published in the README
on purpose. Section 3.9 covers resetting the demo data and changing that password.

> Free plans change. Read each provider's current free-plan terms before you sign up. The limits quoted in section
> 3 come from the providers' documentation at the time of writing.

## 1. What the container needs

| Variable | Required | Example / notes |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | yes | `prod` (set by the image) |
| `SPRING_DATASOURCE_URL` | yes | `jdbc:postgresql://<host>/<database>?sslmode=require` (JDBC format, no user or password inside) |
| `SPRING_DATASOURCE_USERNAME` | yes | the application's database role (it owns the schema so Flyway can migrate) |
| `SPRING_DATASOURCE_PASSWORD` | yes | from the platform's secret store, never in Git |
| `SESSION_COOKIE_SECURE` | yes | `true` behind HTTPS (default in `prod`) |
| `PORT` | no | the HTTP port; defaults to `8080`, and a platform that sets `PORT` (Render does) wins |
| `ATTACHMENT_STORAGE_MODE` | no | `local` (image default; needs a persistent volume at `ATTACHMENT_LOCAL_DIR`), `database` (PostgreSQL `attachment_blobs` table, no disk needed), or `azure` (placeholder, see 4.4) |
| `ATTACHMENT_LOCAL_DIR` | no | `/app/data/attachments` (image default; `local` mode only) |
| `APP_SEED_DEMO_DATA` | no | `false` by default; `true` only for a public demo with synthetic accounts |
| `APP_SEED_DEMO_PASSWORD` | when seeding | shared by the five demo accounts; the public demo uses `demo1234`. Seeding is **skipped** (with a warning in the log) when it is missing or shorter than 8 characters. Re-applied to the demo accounts on every start |
| `JAVA_TOOL_OPTIONS` | no | JVM flags. The image default suits a 512 MB instance: `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError`. Set your own value to override it on larger hosts |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | no | `5` in `prod`. Idle connections close after 5 minutes (`minimum-idle` is 0), so a scale-to-zero database can suspend |
| `AZURE_STORAGE_ACCOUNT`, `AZURE_BLOB_CONTAINER` | for `azure` | names only; authenticate with a managed identity |
| `APP_SWAGGER_UI_ENABLED` | no | `false` by default in `prod` |

Operational facts:

- **Migrations**: Flyway runs automatically at startup before the app accepts traffic, retrying the connection a
  few times in case the database is still waking up. Hibernate only validates the schema (`ddl-auto=validate`).
  Migrations are additive, and applied files are never edited.
- **Health**: `GET /actuator/health` (overall, includes a database check), `/actuator/health/liveness` and
  `/actuator/health/readiness`. Only the status is exposed, and other actuator endpoints are denied. Point platform
  health checks at **readiness**. It does not query the database, so it never keeps a scale-to-zero database awake.
- **Logs**: the `prod` profile writes one JSON object per line (Elastic Common Schema) to stdout, including
  `requestId`. The same id is returned to clients in the `X-Request-Id` header. Passwords, cookies and file contents
  are never logged.
- **Proxy/HTTPS**: `server.forward-headers-strategy=framework` makes the app honour `X-Forwarded-Proto/Host` from
  the platform's TLS proxy. Behind HTTPS the session cookie is `Secure`, `HttpOnly` and `SameSite=Lax`, the CSRF
  cookie is `Secure`, and HSTS is sent. `ProxiedHttpsIT` checks this over real HTTP.
- **Shutdown**: graceful (in-flight requests get up to 20 s).
- **Scale**: run **one instance**. The login throttle and the sessions are held in memory, so several instances
  would need sticky sessions or a shared session store (for example Spring Session JDBC) first. On a free host
  that sleeps, a restart signs everyone out.

## 2. Build and run the image locally

```bash
docker compose -p claimwillow up --build        # db + app on http://localhost:8080 (synthetic demo data)
docker compose -p claimwillow logs -f app
docker compose -p claimwillow down              # keep data
docker compose -p claimwillow down -v           # also delete the local database and evidence volumes
```

Verified locally:

- The image builds, `/actuator/health` reports UP, and the full policyholder/adjuster lifecycle passes against it,
  including an upload.
- Claims and evidence survive `docker compose restart app`.
- With `--memory=512m` and `ATTACHMENT_STORAGE_MODE=database` the image starts, reports UP and passes the same
  lifecycle at both 0.5 and 0.1 CPU. Evidence survives replacing the container. Measurements are in section 3.8.

## 3. Free hosting: Render + Neon (live deployment)

What you get:

- **Render**: a free web service that builds the `Dockerfile` and serves it over HTTPS at
  `https://<service-name>.onrender.com`.
- **Neon**: a free serverless PostgreSQL project.
- **Evidence uploads**: stored in PostgreSQL. Render's free filesystem is ephemeral, so files saved to disk would
  be lost.
- **Cost and accounts**: both services are free plans. Neither needs a change to the code or this repository.

You need about 30 minutes and these accounts:

- the GitHub account that owns this repository;
- a Neon account (https://console.neon.tech);
- a Render account (https://dashboard.render.com). Signing in with GitHub makes connecting the repository easier.

### 3.1 Before you start

1. Make sure the latest commit on `main` has a green CI run (GitHub → **Actions**). The Blueprint deploys only
   commits whose checks pass.
2. Choose a demo password for the synthetic accounts, and keep it in your password manager. It needs at least 12
   characters and at least three of lowercase, uppercase, digits and symbols, and it must not be the README's dev
   password. If it fails these rules, the app starts without demo accounts and logs why. To generate one:
   - PowerShell: `[Convert]::ToBase64String((1..18 | ForEach-Object { Get-Random -Maximum 256 }))`
   - bash: `openssl rand -base64 18`

   Check that the result contains at least one uppercase letter, one lowercase letter and one digit. Generate it
   again if not.

### 3.2 Create the Neon database

1. Sign in to https://console.neon.tech and choose **New project**.
2. Name it `claimwillow`. Pick **PostgreSQL 16** if offered (the app is tested on 16; newer majors are expected
   to work but are untested). For the region, pick **AWS US West 2 (Oregon)**. `render.yaml` places the web service
   in Render's `oregon` region, and keeping both close keeps every query fast.
3. Keep the default database (`neondb`) or create one named `claimwillow`. The app creates its own tables on first
   start.
4. Open the project's **Connect** dialog (on the project dashboard).
5. **Turn the "Connection pooling" toggle off.** You need the **direct** connection string, because Flyway
   migrations must not go through the pooler (Neon recommends direct connections for schema migrations). With 5
   connections at most, the app does not need the pooler.
6. Copy the connection string. It looks like this (placeholders in `<>`):

   ```text
   postgresql://<user>:<password>@ep-<name>-<id>.us-west-2.aws.neon.tech/<database>?sslmode=require&channel_binding=require
   ```

   If the host contains `-pooler`, the toggle is still on. Turn it off and copy again.

### 3.3 Convert the connection string to the three JDBC settings

Spring Boot wants a JDBC URL with the user and password supplied separately:

| Render secret | Value | Taken from |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://ep-<name>-<id>.us-west-2.aws.neon.tech/<database>?sslmode=require&channelBinding=require` | the host and database, with `jdbc:` in front |
| `SPRING_DATASOURCE_USERNAME` | `<user>` | the part before `:` after `postgresql://` |
| `SPRING_DATASOURCE_PASSWORD` | `<password>` | the part between `:` and `@` |

Rules for the conversion:

- Start the URL with `jdbc:postgresql://`, not `postgresql://`.
- Remove `<user>:<password>@` from the URL. Credentials go only in the two separate secrets.
- Keep `sslmode=require`, and write `channel_binding=require` as `channelBinding=require` (JDBC spelling).
- Use the host **without** `-pooler`.

### 3.4 Create the Render service from the Blueprint

1. Sign in to https://dashboard.render.com and choose **New → Blueprint**.
2. Connect your GitHub account if asked, then select the `ClaimWillow` repository. Render reads `render.yaml` and
   shows one web service, `claimwillow`, on the **Free** plan.
3. Render asks for the four values marked `sync: false`:
   - `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` from 3.3;
   - `APP_SEED_DEMO_PASSWORD` from 3.1.

   The rest is already set in `render.yaml`:
   - `SPRING_PROFILES_ACTIVE=prod`
   - `ATTACHMENT_STORAGE_MODE=database`
   - `SESSION_COOKIE_SECURE=true`
   - `APP_SEED_DEMO_DATA=true`
4. Apply the Blueprint. Render asks for these secret values only at this first creation. To change one later,
   open the service → **Environment**, edit the value and save. Render redeploys.

### 3.5 First deploy

- The first build compiles the frontend and backend inside Docker and takes several minutes. Later builds reuse
  cached layers when dependencies have not changed.
- Watch **Logs** on the service. A healthy first start shows, in order:
  1. Flyway applying migrations `1` to `5` to an empty schema;
  2. `Started ClaimWillowApplication`;
  3. `Synthetic demo data is present (idempotent seed complete).`

  If you see `Demo seeding requested but skipped: ...` instead, the demo password failed the rules in 3.1. Set a
  new value in **Environment** and save.
- Render marks the deploy live once `GET /actuator/health/readiness` returns UP. On the free instance's shared CPU,
  JVM startup is much slower than on a laptop. Give the first deploy a few minutes before treating it as failed.
- After this, every push to `main` whose CI passes deploys automatically (`autoDeployTrigger: checksPass`).

### 3.6 Verify the deployment

Open `https://<service-name>.onrender.com` (the URL is at the top of the service page) and check:

1. The sign-in page loads over HTTPS and shows the synthetic-data notice.
2. `https://<service-name>.onrender.com/actuator/health` returns `{"status":"UP"}`. This also proves the database
   connection.
3. Sign in as `priya` with the demo password. Create a claim, attach a small PDF or PNG, and submit it.
   This shows CSRF and the Secure cookie work behind Render's proxy.
4. Sign out, then sign in as `dana`. Assign the claim, request information, decide it and
   close it.
5. Download the evidence as the adjuster. Then sign in as `marcus` and open the same claim URL: it must
   show "not found".
6. Restart the service from the Render dashboard, or redeploy the latest commit through **Manual Deploy**. The
   claim and its evidence must still be there, because both live in Neon.
7. In the browser's developer tools (Application → Cookies), `CLAIMWILLOW_SESSION` must be marked Secure and
   HttpOnly.

When every check passes, add the URL to the README's "Live demo" section. The live demo publishes its password in
the README because the instance holds only synthetic data. If a deployment ever holds anything else, keep its
password out of the repository.

### 3.7 Cold starts and free-plan limits

- **Render sleeps when idle.** A free web service spins down after 15 minutes without inbound traffic. The next
  visitor waits for it to spin up again. Render documents the spin-up as about a minute, and at 0.1 CPU the JVM
  itself needs about two minutes to start (section 3.8), so expect a first load of one to two minutes or a little
  more. The README warns visitors about this.
- **Neon scales to zero** when idle. The first query after a pause waits a few seconds for the database to wake.
  The connection pool closes idle connections (see section 1) so the database can suspend. Platform health checks
  hit readiness, which never queries the database.
- **Render free instance hours**: 750 per workspace per month. A sleeping service uses none.
- **Ephemeral filesystem, no disks** on the free web service. That is why `ATTACHMENT_STORAGE_MODE=database` is set.
- **Restarts** can happen at any time on the free plan. Sessions are held in memory, so users sign in again
  afterwards. Data is not affected.
- **Storage**: Neon's free plan has a small storage allowance (check the current number in your Neon project).
  Evidence files are limited to 5 MB each and 10 per claim, and the demo needs very little space.
- Render's own free PostgreSQL expires after 30 days, which is why this guide uses Neon for the database.

### 3.8 Sizing and troubleshooting

Sizing: the image defaults to `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1
-XX:+ExitOnOutOfMemoryError`:

- The heap gets 70% of the 512 MB container limit, which leaves room for metaspace, threads and buffers.
- The serial collector suits a single, fractional CPU.
- C1-only compilation shortens startup.
- On an out-of-memory error the JVM exits cleanly, so the platform restarts it.

Measured locally (Docker Desktop, production image, `ATTACHMENT_STORAGE_MODE=database`, `--memory=512m`, fresh
database, so Flyway applied V1-V5 and the demo seed ran):

| CPU limit | Container start to readiness UP | Spring "Started ... in" | Memory after startup | Peak memory (cgroup) | Lifecycle smoke |
| --- | --- | --- | --- | --- | --- |
| `--cpus=0.5` | 25.3 s (24.3 s on an existing schema) | 20.3 s | 286 MiB | 313 MiB | passed (296 MiB after) |
| `--cpus=0.1` (Render free CPU share) | 126.0 s | 107.9 s | not sampled | 308 MiB | passed (306 MiB after) |

The smoke test covered sign-in with CSRF, draft, upload, submit, assign, review, request info, response, approval
and close (11 events). It also checked an illegal transition (409), the adjuster download (bytes match), another
policyholder (404) and an anonymous request (401). After the container was deleted and a new one started, the claim
and its evidence were still there. A laptop CPU slice is not identical to Render's, so treat these figures as
estimates.

| Symptom in the Render log | Likely cause and fix |
| --- | --- |
| `Connection to ... refused` / `UnknownHostException` | The URL is not in JDBC form or the host is wrong. Redo 3.3. |
| `password authentication failed` | The username or password secret is wrong, or was copied with the `@`. |
| Flyway errors or hangs during startup | Check whether the host contains `-pooler`. Use the direct host (3.2 step 5). |
| `Demo seeding requested but skipped` | `APP_SEED_DEMO_PASSWORD` is missing or shorter than 8 characters. |
| Deploy times out before going live | The first start at 0.1 CPU is slow. Redeploy once. If it repeats, check the log for an earlier error. |
| `Terminating due to java.lang.OutOfMemoryError` | Unexpected at demo load. Check for very large uploads, or lower `-XX:MaxRAMPercentage` through `JAVA_TOOL_OPTIONS`. |

### 3.9 Live demo maintenance: reset data, change the password

**How the seed treats the demo accounts.** With `APP_SEED_DEMO_DATA=true`, every start reconciles the five demo
accounts (`priya`, `marcus`, `dana`, `sam`, `admin`): missing ones are created, and existing ones get their username,
display name, role and the current `APP_SEED_DEMO_PASSWORD` re-applied (as a BCrypt hash) and are re-enabled.
Policies and claims are only created when missing; existing claims are never changed by the seed.

**Change the demo password** (keeps all data):

1. In Render → `claimwillow` service → **Environment**, set `APP_SEED_DEMO_PASSWORD` to the new value (at least 8
   characters) and save. Render redeploys.
2. In **Logs**, expect `Synthetic demo data is present (idempotent seed complete).` The five accounts now accept the
   new password.
3. Update the password in the README's "Live demo" section and on the sign-in page (`frontend/src/pages/LoginPage.tsx`)
   through a pull request.
4. Sessions live in memory, so anyone already signed in stays signed in until the restart that the deploy performs.

**Reset the demo data** (wipes every claim, note, evidence file and account, then re-seeds):

1. In the Neon console, open the project → **SQL Editor**, choose the database the app uses, and run:

   ```sql
   DROP SCHEMA public CASCADE;
   CREATE SCHEMA public;
   ```

   This deletes everything in that database. Check the project and database name first, and run it only on a
   database that holds synthetic data.
2. In Render, open the `claimwillow` service → **Manual Deploy** → **Restart service** (or **Deploy latest commit**).
3. In **Logs**, expect Flyway applying every migration from `1`, then
   `Synthetic demo data is present (idempotent seed complete).`

**Username migration (V6, V7).** Databases created before usernames existed are upgraded in place on the first start
of a version that has them: V6 gives every account a unique lower-case username (the demo accounts get their short
names; other accounts get the part of the email before `@`, with a short suffix on a clash), and V7 folds the retired
synthetic demo account `elena` into `marcus` (her policy and claims move to Marcus) and removes it. No reset is
needed.

## 4. Alternative: Azure Container Apps + Azure Database for PostgreSQL

Commands use the Azure CLI. Replace `<...>` placeholders; choose one region for everything.

### 4.1 Resource group, registry and image

```bash
az login
az group create -n rg-claimwillow -l <region>
az acr create -g rg-claimwillow -n <acrname> --sku Basic
az acr login -n <acrname>
docker build -t <acrname>.azurecr.io/claimwillow:1.0.0 .
docker push <acrname>.azurecr.io/claimwillow:1.0.0
```

Tag images with the release version (and/or the Git SHA); never deploy `latest`, so rollback is a tag change.

### 4.2 PostgreSQL Flexible Server

```bash
az postgres flexible-server create -g rg-claimwillow -n <pgname> -l <region> \
  --tier Burstable --sku-name <smallest-suitable-sku> --version 16 \
  --admin-user <adminuser> --admin-password '<strong-password>' \
  --public-access None   # or private networking; see below
az postgres flexible-server db create -g rg-claimwillow -s <pgname> -d claimwillow
```

- Prefer private networking (VNet integration shared with the Container Apps environment). If you use public access
  for a first deploy, add a firewall rule only for the Container Apps environment's outbound IPs — not `0.0.0.0/0`.
- Create a dedicated application role (not the server admin) that owns the `claimwillow` database, and use it in
  `SPRING_DATASOURCE_USERNAME`.
- Require TLS (`sslmode=require` in the JDBC URL).
- Automated backups are on by default; set the retention you want and note the point-in-time restore window.

### 4.3 Container Apps environment and app

```bash
az containerapp env create -g rg-claimwillow -n cae-claimwillow -l <region>
az containerapp create -g rg-claimwillow -n claimwillow --environment cae-claimwillow \
  --image <acrname>.azurecr.io/claimwillow:1.0.0 --registry-server <acrname>.azurecr.io \
  --target-port 8080 --ingress external --min-replicas 1 --max-replicas 1 \
  --secrets db-password='<app-db-password>' seed-password='<demo-password>' \
  --env-vars SPRING_PROFILES_ACTIVE=prod SESSION_COOKIE_SECURE=true \
     SPRING_DATASOURCE_URL='jdbc:postgresql://<pgname>.postgres.database.azure.com:5432/claimwillow?sslmode=require' \
     SPRING_DATASOURCE_USERNAME=<app-user> SPRING_DATASOURCE_PASSWORD=secretref:db-password \
     APP_SEED_DEMO_DATA=true APP_SEED_DEMO_PASSWORD=secretref:seed-password
```

- Ingress gives an HTTPS URL (`https://claimwillow.<hash>.<region>.azurecontainerapps.io`); HTTP is redirected.
- Add probes in the portal or YAML: liveness `GET /actuator/health/liveness`, readiness
  `GET /actuator/health/readiness`, startup `GET /actuator/health` with a generous initial delay (~60 s).
- Store secrets in Container Apps secrets or Key Vault references; never in the repository or workflow files.
- Optional: a custom domain with a managed certificate.

### 4.4 Evidence storage

Choose one:

1. **Database storage (no extra service)**: set `ATTACHMENT_STORAGE_MODE=database`, as in the free-hosting path.
   Evidence is stored in PostgreSQL and is covered by the database backups.
2. **Azure Files volume (no code change)** — create a storage account + file share, mount it into the container app at
   `/app/data/attachments`, keep `ATTACHMENT_STORAGE_MODE=local`. The share is private (not web-exposed); downloads
   still go through the app's role checks.
3. **Azure Blob Storage (code change)** — create a private container (public access disabled), give the container
   app a managed identity with *Storage Blob Data Contributor* on that container, implement
   `AzureBlobAttachmentStorage` with the Azure Storage SDK and `DefaultAzureCredential`, then set
   `ATTACHMENT_STORAGE_MODE=azure`, `AZURE_STORAGE_ACCOUNT`, `AZURE_BLOB_CONTAINER`. Until implemented, `azure` mode
   fails fast at startup by design.

### 4.5 Continuous deployment (optional, after the first manual deploy)

Add a separate workflow that runs after CI on `main`, logs in to Azure with GitHub OIDC (federated credential on an
app registration scoped to `rg-claimwillow`), pushes the image tagged with the commit SHA and runs
`az containerapp update --image ...`. Keep it out of the repository until the Azure resources exist.


## 5. Other hosts

Use any reputable managed container host that offers HTTPS, health checks and environment secrets, together with a
managed PostgreSQL 16 service and either a persistent disk or private object storage. Keep the same image and the same
environment variables:

1. Push the image to the provider's registry (or GitHub Container Registry).
2. Create managed PostgreSQL; copy its JDBC URL (with TLS) into `SPRING_DATASOURCE_URL`.
3. Create the service from the image, port 8080, one instance, health check `/actuator/health/readiness`.
4. Set `ATTACHMENT_STORAGE_MODE=database`, or attach a persistent disk at `/app/data/attachments` and keep `local`.
5. Set `SESSION_COOKIE_SECURE=true` and the secrets.

Document in the README which provider was used and any differences (for example disk vs blob storage). Do not
downgrade authentication or storage privacy to make a host work.


## 6. Post-deployment checks

1. The HTTPS URL renders the sign-in page and the synthetic-data notice.
2. Sign in as a demo policyholder; create, save and submit a claim (CSRF works behind the proxy).
3. Sign in as a demo adjuster; assign, review, request info, decide and close.
4. Upload and download evidence; confirm another policyholder gets 404 for the same URL.
5. Restart the app (new revision); data and evidence are still there.
6. `/actuator/health` is UP; logs show JSON lines with `requestId`.

## 7. Operations

### Backups and restore

- Managed PostgreSQL: rely on automated backups and point-in-time restore (restore to a **new** server, then point
  `SPRING_DATASOURCE_URL` at it).
- Manual logical backup: `pg_dump --format=custom --no-owner -h <host> -U <user> claimwillow > claimwillow.dump`;
  restore with `pg_restore --clean --no-owner -d claimwillow claimwillow.dump`. Dumps are never committed.
- Evidence: in `database` mode the files are inside PostgreSQL, so the database backup covers them. Otherwise use
  Azure Files share snapshots or Blob soft delete/versioning.

### Rollback

- Application: redeploy the previous image tag (`az containerapp update --image <acr>/claimwillow:<previous>`) or
  activate the previous revision.
- Database: migrations only add structure. If a release added a migration that the previous image does not
  understand, restore the pre-release backup or roll forward with a fix; never edit or delete an applied migration.

### Credential rotation

1. Create a new database password (or a second application role).
2. Update the `db-password` secret, then restart/redeploy the app.
3. Revoke the old credential. `APP_SEED_DEMO_PASSWORD` is a public demo value rather than a secret; changing it
   and restarting re-applies it to the demo accounts (section 3.9).

### Resetting synthetic demo data

The seed creates missing policies and claims and re-applies the demo accounts' name, role and password. To fully reset a demo database, restore it from a clean
backup or drop and recreate the `claimwillow` database (Flyway rebuilds the schema and the seed repopulates it on the
next start with `APP_SEED_DEMO_DATA=true`). Never run this against a database holding anything but synthetic data.
For the live Render + Neon demo, follow the exact steps in section 3.9.

### Monitoring

Container Apps log streaming / Log Analytics for application logs, platform metrics for restarts and CPU/memory,
PostgreSQL metrics for connections and storage, and a budget alert on the resource group.

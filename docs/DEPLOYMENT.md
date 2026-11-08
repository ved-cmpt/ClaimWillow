# Deployment

ClaimWillow ships as **one container image** (Vite-built React app served by Spring Boot, API under `/api/v1`) plus
**one PostgreSQL database** and **private storage for evidence files**. This document is a step-by-step guide for the
repository owner. Nothing below has been provisioned yet; there is no hosted URL at the time of writing.

> Check current provider pricing before creating anything, set a spending alert, and delete or scale down resources
> you are not using. This guide makes no claims about free tiers or uptime.

## 1. What the container needs

| Variable | Required | Example / notes |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | yes | `prod` (set by the image) |
| `SPRING_DATASOURCE_URL` | yes | `jdbc:postgresql://<host>:5432/claimwillow?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | yes | least-privilege application user (owns the schema for Flyway) |
| `SPRING_DATASOURCE_PASSWORD` | yes | from a secret store, never in Git |
| `SESSION_COOKIE_SECURE` | yes | `true` behind HTTPS (default in `prod`) |
| `PORT` | no | `8080` |
| `APP_SEED_DEMO_DATA` | no | `false` by default; `true` only for a public demo with synthetic accounts |
| `APP_SEED_DEMO_PASSWORD` | when seeding | at least 12 characters, from a secret store; shared by the demo accounts |
| `ATTACHMENT_STORAGE_MODE` | no | `local` (needs a persistent volume at `ATTACHMENT_LOCAL_DIR`) or `azure` (see 4.4) |
| `ATTACHMENT_LOCAL_DIR` | no | `/app/data/attachments` (image default) |
| `AZURE_STORAGE_ACCOUNT`, `AZURE_BLOB_CONTAINER` | for `azure` | names only; authenticate with a managed identity |
| `APP_SWAGGER_UI_ENABLED` | no | `false` by default in `prod` |

Operational facts:

- **Migrations**: Flyway runs automatically at startup before the app accepts traffic. Hibernate only validates the
  schema (`ddl-auto=validate`). Migrations are additive; applied files are never edited.
- **Health**: `GET /actuator/health` (overall), `/actuator/health/liveness`, `/actuator/health/readiness`. Only the
  status is exposed; other actuator endpoints are denied.
- **Logs**: the `prod` profile writes one JSON object per line (Elastic Common Schema) to stdout, including
  `requestId`, which is also returned to clients in the `X-Request-Id` header. Passwords, cookies and file contents
  are never logged.
- **Proxy/HTTPS**: `server.forward-headers-strategy=framework`, so `X-Forwarded-Proto/Host` from the platform's
  ingress are honoured; session cookie is `Secure`, `HttpOnly`, `SameSite=Lax`.
- **Shutdown**: graceful (in-flight requests get up to 20 s).
- **Scale**: run **one replica** for now. The login throttle is in memory and sessions are in the container's
  memory, so multiple replicas would need sticky sessions or a shared session store (for example Spring Session
  JDBC) first.

## 2. Build and run the image locally

```bash
docker compose -p claimwillow up --build        # db + app on http://localhost:8080 (synthetic demo data)
docker compose -p claimwillow logs -f app
docker compose -p claimwillow down              # keep data
docker compose -p claimwillow down -v           # also delete the local database and evidence volumes
```

Verified locally: the image builds, `/actuator/health` reports UP, the full policyholder/adjuster lifecycle
(including an upload) passes against it, and claims and evidence survive `docker compose restart app`.

## 3. Preferred path: Azure Container Apps + Azure Database for PostgreSQL

Commands use the Azure CLI. Replace `<...>` placeholders; choose one region for everything.

### 3.1 Resource group, registry and image

```bash
az login
az group create -n rg-claimwillow -l <region>
az acr create -g rg-claimwillow -n <acrname> --sku Basic
az acr login -n <acrname>
docker build -t <acrname>.azurecr.io/claimwillow:1.0.0 .
docker push <acrname>.azurecr.io/claimwillow:1.0.0
```

Tag images with the release version (and/or the Git SHA); never deploy `latest`, so rollback is a tag change.

### 3.2 PostgreSQL Flexible Server

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

### 3.3 Container Apps environment and app

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

### 3.4 Evidence storage

Choose one:

1. **Azure Files volume (no code change)** — create a storage account + file share, mount it into the container app at
   `/app/data/attachments`, keep `ATTACHMENT_STORAGE_MODE=local`. The share is private (not web-exposed); downloads
   still go through the app's role checks.
2. **Azure Blob Storage (code change)** — create a private container (public access disabled), give the container
   app a managed identity with *Storage Blob Data Contributor* on that container, implement
   `AzureBlobAttachmentStorage` with the Azure Storage SDK and `DefaultAzureCredential`, then set
   `ATTACHMENT_STORAGE_MODE=azure`, `AZURE_STORAGE_ACCOUNT`, `AZURE_BLOB_CONTAINER`. Until implemented, `azure` mode
   fails fast at startup by design.

### 3.5 Continuous deployment (optional, after the first manual deploy)

Add a separate workflow that runs after CI on `main`, logs in to Azure with GitHub OIDC (federated credential on an
app registration scoped to `rg-claimwillow`), pushes the image tagged with the commit SHA and runs
`az containerapp update --image ...`. Keep it out of the repository until the Azure resources exist.

## 4. Fallback path (if Azure cost or permissions block you)

Use any reputable managed container host that offers HTTPS, health checks and environment secrets, together with a
managed PostgreSQL 16 service and either a persistent disk or private object storage. Keep the same image and the same
environment variables:

1. Push the image to the provider's registry (or GitHub Container Registry).
2. Create managed PostgreSQL; copy its JDBC URL (with TLS) into `SPRING_DATASOURCE_URL`.
3. Create the service from the image, port 8080, one instance, health check `/actuator/health`.
4. Attach a persistent disk at `/app/data/attachments` (or implement an object-storage `AttachmentStorage`).
5. Set `SESSION_COOKIE_SECURE=true` and the secrets.

Document in the README which provider was used and any differences (for example disk vs blob storage). Do not
downgrade authentication or storage privacy to make a host work.

## 5. Post-deployment checks

1. The HTTPS URL renders the sign-in page and the synthetic-data notice.
2. Sign in as a demo policyholder; create, save and submit a claim (CSRF works behind the proxy).
3. Sign in as a demo adjuster; assign, review, request info, decide and close.
4. Upload and download evidence; confirm another policyholder gets 404 for the same URL.
5. Restart the app (new revision); data and evidence are still there.
6. `/actuator/health` is UP; logs show JSON lines with `requestId`.

## 6. Operations

### Backups and restore

- Managed PostgreSQL: rely on automated backups and point-in-time restore (restore to a **new** server, then point
  `SPRING_DATASOURCE_URL` at it).
- Manual logical backup: `pg_dump --format=custom --no-owner -h <host> -U <user> claimwillow > claimwillow.dump`;
  restore with `pg_restore --clean --no-owner -d claimwillow claimwillow.dump`. Dumps are never committed.
- Evidence: Azure Files share snapshots or Blob soft delete/versioning.

### Rollback

- Application: redeploy the previous image tag (`az containerapp update --image <acr>/claimwillow:<previous>`) or
  activate the previous revision.
- Database: migrations only add structure. If a release added a migration that the previous image does not
  understand, restore the pre-release backup or roll forward with a fix; never edit or delete an applied migration.

### Credential rotation

1. Create a new database password (or a second application role).
2. Update the `db-password` secret, then restart/redeploy the app.
3. Revoke the old credential. Rotate `APP_SEED_DEMO_PASSWORD` the same way (existing seeded accounts keep their old
   password until they are re-created; reset demo data below).

### Resetting synthetic demo data

The seed only creates missing accounts, policies and claims. To fully reset a demo database, restore it from a clean
backup or drop and recreate the `claimwillow` database (Flyway rebuilds the schema and the seed repopulates it on the
next start with `APP_SEED_DEMO_DATA=true`). Never run this against a database holding anything but synthetic data.

### Monitoring

Container Apps log streaming / Log Analytics for application logs, platform metrics for restarts and CPU/memory,
PostgreSQL metrics for connections and storage, and a budget alert on the resource group.

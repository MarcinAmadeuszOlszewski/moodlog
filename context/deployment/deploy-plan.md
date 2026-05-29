# First Railway Deployment Plan

## Scope

- Deploy the current stateless MoodLog MVP to Railway from the repository root.
- Keep release 1 narrow: Railway-generated public URL only, no CI/CD rollout, no custom domain, no database provisioning, and no auth/AI rollout.

## Current repository contract

- Runtime: Java 21, Spring Boot 4.0.6, Maven Wrapper.
- Build artifact: `target\mood-log-0.0.1-SNAPSHOT.jar`
- Port contract: `src\main\resources\application.properties` uses `server.port=${PORT:8080}`, so the app will listen on Railway's injected `PORT` and still default to `8080` locally.
- Smoke routes:
  - `GET /` returns HTML containing `Witaj! Jesteś dziś` and the frontend fetch to `/v1/random`
  - `GET /v1/random` returns a numeric response body
- No user-defined application secrets are required for the current app shape.

## Execution status

- Local Maven package build is green (`.\mvnw.cmd package`, 2026-05-29).
- Local JAR startup was verified against `PORT=9090`.
- Live Railway deployment was executed successfully from this environment on 2026-05-29.
- Railway project `moodlog-prod` (`a34dff9c-2d97-4b56-bd14-e94f65326027`) is linked to service `web` (`85e0ac4e-e589-4f32-a09d-c0d1553e17dc`) in `production`.
- Deployment `dec2bcc9-eac5-40c4-bbfe-4af26e82c204` is `SUCCESS`, the service is `Online` in `sfo`, and the generated public domain is `https://web-production-6979d.up.railway.app`.

## Manual gates

1. Human installs/authenticates Railway CLI and selects the right workspace/project.
2. Human approves public domain exposure for the first live environment.
3. Human performs any dashboard-only rollback action if production needs to move back to an older deployment.

## Railway CLI runbook

### 1. Install CLI

```powershell
scoop install railway
# or
npm install -g @railway/cli
```

### 2. Authenticate

```powershell
railway login
# headless alternative
railway login --browserless
```

### 3. Initialize or link the project

Run from `C:\workshop\10xDevV3`:

```powershell
railway init
# if the Railway project already exists
railway link
```

### 4. Execute the first deploy

```powershell
railway up --ci
```

### 5. Inspect deployment state

```powershell
railway status
railway logs --follow
railway deployment list --json
```

### 6. Generate a Railway public domain

```powershell
railway domain
```

### 7. Apply future config changes

```powershell
railway variable set KEY=value
railway redeploy
```

## Smoke test checklist

After Railway returns a `*.up.railway.app` domain:

```powershell
curl.exe -sS https://<generated-domain>/
curl.exe -sS https://<generated-domain>/v1/random
```

Expected results:

- `/` returns HTML containing `Witaj! Jesteś dziś`
- `/` still contains the frontend reference to the random endpoint
- `/v1/random` returns only digits

Actual results from the live deployment:

- `/` returned the expected homepage HTML, including the welcome copy and the frontend fetch to the random endpoint.
- `/v1/random` returned digits during the post-deploy smoke test (`42973` at verification time).

## Rollback path

1. Inspect recent deployments:

```powershell
railway deployment list --json
railway logs --deployment <deployment-id>
```

2. If the issue is caused by a config-only change, try:

```powershell
railway redeploy
```

3. If you must restore an older successful deployment, use the Railway dashboard path:

`Service -> Deployments -> Rollback`

4. Remember that code rollback does not roll back future database/schema changes.

## Out of scope

- Custom domain setup
- CI/CD or GitHub Actions
- Database provisioning
- Auth or journal persistence rollout

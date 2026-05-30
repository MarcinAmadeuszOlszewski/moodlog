# Deployment Plan

## Problem

Prepare the first deployment plan for MoodLog based on `context\foundation\infrastructure.md` and `context\foundation\tech-stack.md`, with a human review gate before any platform changes.

## Current state

- The app is a minimal Java 21 / Spring Boot 4.0.6 / Maven web app.
- Current HTTP surface is small and stateless: `GET /` renders the Thymeleaf page and `GET /v1/random` returns a number.
- There is no database, auth flow, AI integration, background worker, Dockerfile, `railway.json`, or CI workflow in the repo yet.
- `context\foundation\infrastructure.md` recommends Railway for MVP hosting.
- `context\foundation\tech-stack.md` still contains starter metadata `deployment_target: fly`, but Railway is the confirmed platform for the first deployment.
- The runtime contract now includes `server.port=${PORT:8080}`, so the app can bind to Railway's injected port without changing local development defaults.

## Proposed approach

1. Use Railway as the deployment source of truth for this first release and treat the Fly value in `tech-stack.md` as starter metadata that no longer drives infrastructure decisions.
2. Keep the first deployment narrow: deploy the current app shape only, without bundling CI/CD, database provisioning, or custom-domain work unless explicitly requested.
3. Use the platform CLI path described in `infrastructure.md`, with manual gates for authentication, secret entry, public exposure, and any destructive or dashboard-only actions.
4. During implementation, produce `context\deployment\deploy-plan.md` with exact commands, manual checkpoints, rollback notes, and smoke-test steps before making live changes.
5. Verify the deployed service against the current app contract: homepage render on `/` and numeric response on `/v1/random`.

## Todos

1. **Reviewing runtime contract**
   Confirm the Maven build/start path needed for the chosen platform and identify whether extra platform files are actually necessary for the first deploy.
2. **Drafting repository deployment artifact**
   Prepare `context\deployment\deploy-plan.md` with exact CLI commands, manual gates, secrets checklist, rollback path, and verification checklist.
3. **Executing first Railway deployment**
   Authenticate CLI, initialize/link the service, push the first deploy, and expose only the minimum public surface needed for MVP verification.
4. **Running production smoke checks**
   Validate `/`, `/v1/random`, deployment logs/status, and record follow-up gaps separately from the first release.

## Notes

- Railway is confirmed as the platform for the first deployment; the Fly entry in `tech-stack.md` is treated as stale starter metadata.
- The plan currently assumes the first release should use the platform-generated public URL, not a custom domain.
- For the current codebase, no application secrets are visible yet beyond future platform or account credentials.
- CI/CD remains out of scope for this first manual deployment unless the scope changes.
- Runtime validation is done locally: the Maven package build passes and the repackaged JAR responds correctly on `PORT=9090`.
- `context\deployment\deploy-plan.md` has been executed: Railway project `moodlog-prod`, service `web`, and deployment `dec2bcc9-eac5-40c4-bbfe-4af26e82c204` are live at `https://web-production-6979d.up.railway.app`, and the `/` plus `/v1/random` smoke checks passed.

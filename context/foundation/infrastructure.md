---
project: MoodLog
researched_at: 2026-05-28T00:00:00Z
recommended_platform: Railway
runner_up: Fly.io
context_type: mvp
tech_stack:
  language: Java
  framework: Spring Boot 4.0.6
  runtime: Java 21
---

## Recommendation

**Deploy on Railway.**

Railway and Fly.io tied on the weighted platform criteria once the Java runtime filter removed Cloudflare, Vercel, and Netlify. Railway wins the tie-break for this MVP because the project is a small Spring Boot app, the app is request/response only, developer experience matters more than shaving the last dollar, and Railway can deploy the Maven app directly without introducing Docker on day one. Because external providers are acceptable, the main Railway weakness - self-managed database templates - can be avoided later by pairing Railway app hosting with an external managed Postgres provider.

## Platform Comparison

| Platform | CLI-first | Managed/Serverless | Agent-readable docs | Stable deploy API | MCP / Integration | Total | Result |
|---|---|---|---|---|---|---|---|
| Cloudflare Workers + Pages | Pass | Pass | Pass | Pass | Pass | 5.0 | Dropped - no JVM runtime |
| Vercel | Pass | Pass | Pass | Pass | Partial | 4.5 | Dropped - no Spring Boot runtime |
| Netlify | Pass | Pass | Partial | Pass | Pass | 4.0 | Dropped - no long-lived Java runtime |
| Railway | Partial | Pass | Pass | Partial | Pass | 4.0 | Shortlist #1 |
| Fly.io | Pass | Partial | Pass | Pass | Partial | 4.0 | Shortlist #2 |
| Render | Partial | Pass | Partial | Pass | Partial | 3.5 | Shortlist #3 |

**Cloudflare Workers + Pages** scored perfectly on the agent-friendly lens because the CLI, docs, deploy flow, and MCP story are all strong. It still fails the hard filter because Workers/Pages do not run a JVM process or a standalone Spring Boot app, so it cannot host this stack.

**Vercel** also scored well on the agent lens and is especially strong for frontend-first frameworks. It was dropped because this project is a Java 21 Spring Boot application, and Vercel is not a fit for running the app as a persistent JVM web service.

**Netlify** remains strong for JAMstack workflows and agent integration, but it was filtered out for the same reason: it does not provide the right runtime shape for a long-lived Spring Boot service.

**Railway** fits the stack directly, supports persistent processes, has strong CLI coverage for deploys/logs/variables, exposes agent-oriented CLI capabilities, and has docs that are easy for agents to consume. It loses full marks on CLI-first maintenance and stable deploy API because rolling back to an older successful deployment is still a dashboard action tied to plan retention, not a first-class CLI workflow.

**Fly.io** is the strongest pure agent-ops alternative because its CLI covers deploy, logs, and rollback cleanly, and the docs are solid. It scores lower for MVP fit because Spring Boot on Fly.io is effectively a Docker-first path, which adds operational work the project does not need yet.

**Render** remains viable, but it trails the top two because the best Java path is also Docker-based, its CLI/API story is less central to the workflow than Fly.io, and its cost floor for an always-on JVM app is less attractive than Railway for this specific MVP.

### Shortlisted Platforms

#### 1. Railway (Recommended)

Railway won because it matches the current Spring Boot app with the least setup friction. For a solo, after-hours MVP, fast deploys, minimal platform ceremony, strong docs, and built-in agent tooling matter more than maximizing rollback purity or low-level infrastructure control.

#### 2. Fly.io

Fly.io finished second because it is the most complete CLI-first choice for agent-driven operations and gives cleaner rollback mechanics than Railway. It lost mainly on MVP ergonomics: Docker, machine sizing, and JVM tuning move more operational burden onto the project earlier than necessary.

#### 3. Render

Render stayed in the top three because it can host the app reliably and offers a stable deployment surface. It ranked below Fly.io because it does not offset its Docker-first Java path with better CLI-first operations or lower likely cost for this app.

## Anti-Bias Cross-Check: Railway

### Devil's Advocate - Weaknesses

1. Railway's database templates are easy to mistake for fully managed databases, but the operational responsibility still sits with the team unless they choose an external managed provider.
2. The current platform story is strongest for deploy, logs, and variables; rollback to an older successful deployment is still more dashboard-native than CLI-native.
3. Railway's smooth developer experience can hide the real paid floor of a JVM service until the team starts opening PR environments and burning usage credits.
4. Build tooling terminology has shifted over time, so older Nixpacks examples can conflict with the current Railpack-oriented behavior and create avoidable Java build confusion.
5. A Spring Boot app can outgrow an initially comfortable memory allocation quietly, and the first signal may be a degraded deployment rather than a neat compile-time warning.

### Pre-Mortem - How This Could Fail

Six months later, the team realizes Railway was only "easy" because they never forced themselves to model the operational edges up front. They deployed quickly, kept using the default database template, and treated it like a fully managed service without setting clear backup ownership or restore drills. When the schema changed under real user data, they discovered that rolling code back was easier than rolling data back, and the team had no rehearsal for that gap. At the same time, the app had slowly become heavier: auth, journal history, AI calls, and trend queries pushed memory use beyond the tiny-MVP shape. Because Railway had worked so well early on, no one had set alert thresholds or cost limits, so the first real incident combined a performance problem with a surprise bill. Recovery was slower because some internal docs and examples still referred to older builder behavior, which caused confusion during a redeploy. None of this happened because Railway was a bad platform; it happened because the team accepted its convenience without explicitly managing the hidden constraints that convenience deferred.

### Unknown Unknowns

- Railway app hosting is easy, but the safest data story for this PRD may be Railway for the app plus an external managed Postgres provider, not "everything on Railway."
- PR environments are useful, but they still consume billable resources; a burst of pull requests can create spend that feels surprising on a small project.
- Rolling back code does not roll back database schema or user data automatically, so the migration strategy matters from the first real database change.
- Some Railway documentation in the wild still references older builder naming and behaviors, so version drift in examples is a real source of setup mistakes.
- If the app later adds SSE, heavier uploads, or background workers for AI processing, the current low-ops fit should be re-evaluated instead of assumed.

## Operational Story

- **Preview deploys**: Railway PR Environments can create isolated preview deployments for pull requests and remove them when the PR closes or merges; treat those URLs as public unless app auth or an external access layer protects them.
- **Secrets**: Store environment variables per Railway environment and manage them with `railway variables set KEY=VALUE`; project members with Railway access can read or change them, so primary secret rotation should stay a deliberate team action.
- **Rollback**: Use `railway deployment list --json` and `railway logs <deployment-id>` to identify the target deployment, then roll back a previous successful deployment from the Service -> Deployments UI; the rollback restores the image and custom variables, but not database schema or data.
- **Approval**: An agent may create preview deploys, inspect logs, and perform non-destructive redeploys; a human should approve production domain exposure, primary secret rotation, destructive data changes, and dashboard-only rollback actions.
- **Logs**: Read runtime and deployment logs with `railway logs`, `railway logs --json`, and `railway deployment list --json`; use `railway status` for the currently linked service and environment.

## Risk Register

| Risk | Source | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| Database template treated like fully managed infrastructure | Devil's advocate | M | H | Prefer Railway app hosting plus an external managed Postgres provider once auth and journaling land, or set explicit Railway backup/restore ownership before storing user data. |
| UI-assisted rollback breaks unattended recovery | Devil's advocate / Research finding | M | M | Keep migrations backward-compatible, record deployment IDs in release notes, and assign a human owner for dashboard rollback until Railway exposes a first-class non-interactive flow. |
| PR previews and JVM runtime create surprise spend | Devil's advocate / Pre-mortem | M | M | Set Railway hard usage limits, review PR environment defaults early, and start with one small paid environment instead of treating the platform like a free tier. |
| Builder example drift causes broken Java deploys | Unknown unknowns | M | M | Pin Java 21 explicitly in the project, use current Railway docs, and confirm the first preview build before automating production deploys. |
| Code rollback does not undo data changes | Pre-mortem / Unknown unknowns | H | H | Use additive migrations, back up before destructive schema work, and rehearse one restore before the first production data migration. |
| App memory needs outgrow the initial sizing quietly | Pre-mortem | M | H | Track memory from the first real environment, cap heap deliberately, and revisit the platform decision if AI/background work shifts the app out of the simple request/response shape. |

## Getting Started

1. Install the Railway CLI, authenticate, and link the project: `railway login` then `railway init` in `mood-log`.
2. Deploy the current Spring Boot app from the repo root with `railway up --ci`; Railway can build the Maven project directly from `pom.xml` without requiring a Dockerfile.
3. Add environment variables with `railway variables set KEY=VALUE` and redeploy with `railway redeploy` when configuration changes.
4. Generate a public domain for the service in Railway Networking, then verify `GET /` and `GET /v1/random`.
5. When auth and journaling storage are added, connect an external managed Postgres first, then automate preview deploys for pull requests if the monthly usage limit still looks healthy.

## Out of Scope

The following were not evaluated in this research:
- Docker image configuration
- CI/CD pipeline setup
- Production-scale architecture (multi-region, HA, DR)

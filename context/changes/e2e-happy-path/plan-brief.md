# E2E Happy-Path Playwright Tests — Plan Brief

> Full plan: `context/changes/e2e-happy-path/plan.md`
> Research: `context/changes/e2e-happy-path/research.md`

## What & Why

Wire the existing Playwright 1.60.0 dependency into the project's `@SpringBootTest` test infrastructure and implement five browser-driven happy-path scenarios. All four product slices (S-01–S-04) are complete; this change adds end-to-end confidence that the full user journey works as a real browser would experience it — something `MockMvc` integration tests cannot verify.

## Starting Point

Playwright 1.60.0 is declared in `pom.xml` with wrong scope (`compile`). No Maven E2E lifecycle configuration exists (no Failsafe, no start/stop). Zero E2E test files exist. The web surface, form selectors, auth flow, and redirect chains are fully mapped in `research.md`.

## Desired End State

`.\mvnw.cmd test -Dgroups=e2e` runs five ordered browser scenarios green in ~15-30 seconds. `.\mvnw.cmd test` (no flags) continues to run only fast unit and integration tests with E2E excluded. The five scenarios cover: register → /journal, create entry → appears in list, history page, trends page, logout + login.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
|---|---|---|---|
| Maven lifecycle approach | `@SpringBootTest(RANDOM_PORT)` + Playwright Java API | Reuses existing test profile (H2, AI disabled) with no new Maven plugin config | Plan |
| E2E Spring profile | Reuse existing test profile | AI is already disabled via `moodlog.ai.enabled=false`; H2 is sufficient for happy-path | Research |
| Test class structure | Single `JournalHappyPathE2ETests` with 5 ordered methods | One class, one browser context, one user — reads as a natural user journey | Plan |
| Test isolation | One shared user + shared `BrowserContext` across methods | `@BeforeAll` registers user once; session cookie persists in `BrowserContext` between scenarios | Plan |
| Mood assertion | Assert entry appears in list only (no mood tag) | Mood correctness already covered by Phase 1 unit tests; avoids brittle label-text coupling | Plan |
| Test separation | `@Tag("e2e")` + Surefire `excludedGroups` | One pom.xml config line keeps fast tests fast; E2E runs with explicit `-Dgroups=e2e` flag | Plan |
| `@TestInstance(PER_CLASS)` | Required | Only way `@LocalServerPort` (instance field) is available when `@BeforeAll` runs | Plan |

## Scope

**In scope:**
- Fix Playwright dependency scope (`compile` → `test`)
- Add Surefire `excludedGroups=e2e` config
- `JournalHappyPathE2ETests.java` with 5 ordered scenarios
- One-time browser binary install step (developer machine setup)

**Out of scope:**
- CI wiring (local only)
- Failsafe plugin or spring-boot-maven-plugin start/stop
- Error-path scenarios (invalid credentials, duplicate registration)
- S-04 edit/delete/mood-override flows
- Mood tag assertions

## Architecture / Approach

`@SpringBootTest(webEnvironment = RANDOM_PORT)` starts real embedded Tomcat on a random port. `@TestInstance(PER_CLASS)` allows `@LocalServerPort` injection before `@BeforeAll` runs. `@BeforeAll` cleans the H2 DB, launches Playwright Chromium, and creates one `BrowserContext`. Five `@Order`-annotated test methods share that context, carrying cookies and session across scenarios. `@AfterAll` closes the browser. The `@Tag("e2e")` + Surefire exclusion keeps `.\mvnw.cmd test` fast.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. Maven Build Wiring | Correct Playwright scope; Surefire excludes E2E from default run | Surefire version conflict with Spring Boot BOM (unlikely — BOM manages it) |
| 2. E2E Test Class | 5 green browser scenarios; fast test suite unaffected | `@TestInstance(PER_CLASS)` missed → `@LocalServerPort` injects as 0 |

**Prerequisites:** Playwright Chromium binaries installed on dev machine (one-time `.\mvnw.cmd exec:java` command — see Phase 1 manual criteria)  
**Estimated effort:** ~1 session across 2 phases

## Open Risks & Assumptions

- Playwright `Playwright.create()` fails if browser binaries not installed — must run install command once before Phase 2
- Spring context caching: if other `@SpringBootTest` tests reuse this app context, `@BeforeAll` DB cleanup may affect their data; risk is low because `RANDOM_PORT` creates a distinct context cache key from the `MOCK` webEnvironment used by existing tests
- `StubMoodClassifier` is in `src/main/java` (production code), active when `moodlog.ai.enabled=false` — any test that writes an entry with empty content will throw `MoodClassificationFailedException(INVALID_INPUT)`; test entry content must be non-empty

## Success Criteria (Summary)

- `.\mvnw.cmd test -Dgroups=e2e` passes with all 5 scenarios green
- `.\mvnw.cmd test` (no flags) excludes E2E and all existing tests still pass
- One visual headed-mode run confirms browser flows match expected navigation

# Test Plan

> Phased test rollout for this project. Strategy is frozen at the top
> (§1–§5); cookbook patterns at the bottom (§6) fill in as phases ship.
> Read before writing any new test.
>
> Refresh: re-run `/10x-test-plan-spring --refresh` when stale (see §8).
>
> Last updated: 2026-06-12 (Phase 1 researched)

---

## 1. Strategy

Tests follow three non-negotiable principles for this project:

1. **Cost × signal.** The cheapest test that gives a real signal for the
   risk wins. Do not promote to e2e because e2e "feels safer." Do not put a
   vision model on top of a deterministic visual diff that already catches
   the regression.
2. **User concerns are first-class evidence.** Risks anchored in "the
   team is worried about X, and the failure would surface somewhere in
   <area>" carry the same weight as PRD lines or hot-spot data.
3. **Risks are scenarios, not code locations.** This plan documents *what
   could fail* and *why we believe it's likely* — drawn from documents,
   interview, and codebase *signal* (churn, structure, test base). It does
   NOT claim to know which line owns the failure. That knowledge is
   produced by `/10x-research-spring` during each rollout phase. If the plan and
   research disagree about where the failure lives, research is the
   ground truth.

Hot-spot scope used for likelihood weighting: `src/main/java`, `src/main/resources`, `src/test/java` — 21 commits / 30 days.

---

## 2. Risk Map

The top failure scenarios this project must protect against, ordered by
risk = impact × likelihood. Risks are failure scenarios in user / business
terms, not test names. The Source column cites the *evidence that surfaced
this risk* — never a specific file as "where the failure lives" (that is
research's job, see §1 principle #3).

| # | Risk (failure scenario) | Impact | Likelihood | Source (evidence — not anchor) |
|---|---|---|---|---|
| 1 | AI classification times out or errors → user's journal entry is silently lost despite PRD requiring a fallback save-with-unknown-mood | High | High | Interview Q1; PRD NFR: "A failed mood-classification request does not prevent the entry from being saved" |
| 2 | AI classifier response contract changes (missing/null fields, out-of-range score) → parsing silently breaks; all new entries fail to classify with no test catching it | High | High | Interview Q2 (user burned before); hot-spot `src/main/java/com/amadeuszx/moodlog` — 21 commits/30d |
| 3 | Mood trends time-zone regression → entry near Warsaw midnight placed in the wrong calendar day or week; user sees incorrect mood trend | High | High | Interview Q3 (user changes this without confidence); hot-spot `src/main/resources/templates` — 7 commits/30d |
| 4 | S-04 (edit/delete/mood-override) ships without per-resource ownership check → authenticated user A mutates user B's entry | High | Medium | Interview Q4; PRD FR-004/FR-006; security lens (IDOR on owned-resource operations) |
| 5 | Security config change accidentally removes route protection → anonymous GET on /journal, /journal/history, or /journal/trends succeeds | High | Medium | Hot-spot `src/main/java/com/amadeuszx/moodlog` — SecurityConfiguration.java 6 commits/30d |
| 6 | Flyway migration SQL compatible with H2 but invalid against PostgreSQL in prod → app fails to start; schema state unknown | High | Medium | Tech-stack: H2 in-memory for all tests, PostgreSQL in production; infrastructure.md; pom.xml |

### Risk Response Guidance

| Risk | What would prove protection | Must challenge | Context `/10x-research-spring` must ground | Likely cheapest layer | Anti-pattern to avoid |
|---|---|---|---|---|---|
| #1 | Entry is persisted with an unknown mood tag and the user sees the saved entry when the classifier throws or times out; no data is lost | "classifier exception = entry not saved is the correct behavior" (current service behavior may contradict PRD NFR; research must verify the intended fallback path) | JournalEntryService save/classify flow; how a timeout is distinguished from a structured failure; whether a fallback unknown-mood path exists or needs to be added | MockMvc integration test with mocked classifier throwing a timeout-style exception | Asserting 0L entries on classifier failure as "passing"; happy-path-only without testing timeout/exception branches |
| #2 | A response with a missing or null required field, or a score outside 0–100, is caught and mapped to `MoodClassificationFailedException` with `INVALID_RESPONSE` reason without crashing the application | "the happy path works means all parsing paths work"; "tests that mock the classifier cover actual parsing logic" | OpenAiMoodClassifier parsing: which fields are mandatory; score range validation; what a realistic malformed or partial AI response looks like | Unit test on OpenAiMoodClassifier with realistic partial/missing/out-of-range response shapes | Implementation mirror (expected values copied from parsing logic); not covering null/empty content or out-of-bounds score fields |
| #3 | An entry created at 22:05 UTC (00:05 Europe/Warsaw) is attributed to the *next* Warsaw calendar day; 7-day and 30-day windows align to completed Warsaw days, not UTC days | "fixed-clock tests using UTC noon timestamps cover all boundary cases" | JournalEntryService getTrendView; how Europe/Warsaw day boundaries are computed for each Instant; which existing Clock injection points enable precise boundary testing | Unit test (service) with Instant values carefully placed around the Warsaw midnight boundary | Using only UTC-noon timestamps that never probe the boundary; asserting entry counts without asserting which specific day a boundary entry belongs to |
| #4 | PUT/PATCH/DELETE on an entry ID belonging to user B returns 403 or 404 when user A is authenticated and makes the request | "isAuthenticated() implies authorized to mutate this specific resource"; "repository scoping on reads implies the same protection on writes" | JournalController edit/delete/override endpoints (once built); where ownership is checked relative to retrieval; whether the error response is 403 (forbidden) or 404 (not found) | MockMvc test with `.with(user(...))` on a cross-user mutation attempt | Testing only same-user happy path; not testing what happens when the entryId belongs to a different authenticated user |
| #5 | Anonymous GET /journal, /journal/history, and /journal/trends each redirect to /login; any new route addition does not bypass the deny-all fallback | "testing /journal covers /journal/history and /journal/trends"; "a route not in the public list is implicitly denied" | SecurityConfiguration route list; how the deny-all fallback is configured; which routes are explicitly public vs. protected | MockMvc anonymous GET on each distinct protected route pattern | Testing only one representative protected path; assuming sibling routes share the same protection without asserting it |
| #6 | Flyway V1 and V2 migrations apply cleanly against a real PostgreSQL instance; schema is queryable after all migrations run; no H2-specific SQL present | "H2 tests pass means PostgreSQL migrations work"; "SERIAL vs IDENTITY and VARCHAR(n) are fully compatible between H2 and PostgreSQL" | Migration SQL contents; which PostgreSQL-specific or H2-specific constructs are present; Testcontainers availability and setup for Spring Boot 4.x | `@SpringBootTest` with a Testcontainers PostgreSQL profile | Skipping because H2 tests pass; not asserting post-migration schema shape |

---

## 3. Phased Rollout

Each row is a discrete rollout phase that will open its own change folder
via `/10x-new`. Status moves left-to-right through the values below; the
orchestrator updates Status as artifacts appear on disk.

| # | Phase name | Goal (one line) | Risks covered | Test types | Status | Change folder |
|---|---|---|---|---|---|---|
| 1 | AI boundary hardening | Prove entry durability on AI failure and verify classifier response contract | #1, #2 | unit + integration | planned | testing-ai-boundary-hardening |
| 2 | Trends time-zone accuracy | Prove mood trend calculations are correct across Europe/Warsaw midnight boundaries | #3 | integration (service + MockMvc) | not started | — |
| 3 | Ownership + security enforcement | Ensure S-04 edit/delete/override verifies per-resource ownership; prevent security config regressions | #4, #5 | integration (MockMvc + spring-security-test) | not started | — |
| 4 | Migration safety gate | Verify Flyway migrations apply cleanly against real PostgreSQL via Testcontainers | #6 | integration (Testcontainers) | not started | — |

---

## 4. Stack

The classic test base for this project. AI-native tools (if any) carry a
`checked:` date so future readers can see which lines need re-verification.

| Layer | Tool | Version | Notes |
|---|---|---|---|
| Unit tests | JUnit 5 + `@ExtendWith(MockitoExtension.class)` | bundled with Spring Boot 4.0.6 | For isolated business-logic and classifier parsing tests |
| Integration (web + security) | `@SpringBootTest` + MockMvc + `spring-security-test` | bundled with Spring Boot 4.0.6 | Set up via `MockMvcBuilders.webAppContextSetup(...).apply(springSecurity())` |
| Security test helpers | `spring-security-test` | bundled with Spring Boot 4.0.6 | `.with(user(...).roles("USER"))` + `.with(csrf())` for authenticated MockMvc requests |
| DB / repository | H2 in-memory | bundled; configured via `application.properties` | Current test DB; PostgreSQL compatibility not verified in tests |
| DB / PostgreSQL parity | Testcontainers + PostgreSQL | none yet — see §3 Phase 4 | Required for migration safety gate |
| Clock injection | `FixedClockConfiguration` (`@TestConfiguration`) | bundled | Fixed `Instant.parse("2026-06-01T10:00:00Z")` used in trend tests |
| Log assertion | `OutputCaptureExtension` | bundled with Spring Boot 4.0.6 | Used in auth and classification log-safety tests |
| E2E / browser | Playwright | 1.60.0 (in pom.xml) | Dependency present but no E2E tests written; not required by any current rollout phase |
| Wrapper commands | `.\mvnw.cmd test` / `.\mvnw.cmd spring-boot:run` | Maven Wrapper | Use these for all test and run commands on Windows |

**Stack grounding tools (current session):**
- Docs: Context7 — available; Spring Boot 4.x + Spring AI 2.0.0-M8 doc resolution possible; not queried for this initial write (existing test files provided sufficient signal); checked: 2026-06-12
- Search: Exa.ai — available; not queried for this initial write; available for research phases to verify current Testcontainers + Spring Boot 4.x setup; checked: 2026-06-12
- Runtime/browser: Playwright MCP — not available as a session tool; Playwright 1.60.0 dependency exists in pom.xml for potential future E2E; not used; checked: 2026-06-12
- Provider/platform: GitHub MCP — available; relevant for CI quality-gate wiring in future phases; checked: 2026-06-12

---

## 5. Quality Gates

The full set of gates that must pass before a change reaches production.

| Gate | Where | Required? | Catches |
|---|---|---|---|
| unit + integration (`.\mvnw.cmd test`) | local + CI | required now | Logic regressions, routing errors, auth failures |
| Classifier contract tests | local + CI | required after §3 Phase 1 | AI response parsing failures, entry durability regressions |
| Trends boundary tests | local + CI | required after §3 Phase 2 | Time-zone miscalculations in mood trend windows |
| Ownership + security tests | local + CI | required after §3 Phase 3 | IDOR on edit/delete/override, security config regressions |
| Migration safety (Testcontainers) | local + CI | required after §3 Phase 4 | Flyway migration failures against PostgreSQL |
| E2E on critical flows | CI on PR | optional — not in current rollout | Full browser-stack regressions on journal flow |

---

## 6. Cookbook Patterns

How to add new tests in this project. Each sub-section is filled in once
the relevant rollout phase ships; before that, the sub-section reads
"TBD — see §3 Phase N."

### 6.1 Adding a unit test for business logic or classifier parsing

TBD — see §3 Phase 1 for the classifier response contract and entry durability failure-mode patterns.

### 6.2 Adding a Spring integration test (MockMvc + security)

TBD — see §3 Phase 1 (AI boundary) and Phase 3 (ownership enforcement) for MockMvc + `spring-security-test` patterns.

### 6.3 Adding a trend boundary test

TBD — see §3 Phase 2 for the Europe/Warsaw midnight boundary pattern and `FixedClockConfiguration` usage.

### 6.4 Adding an ownership verification test for a write endpoint

TBD — see §3 Phase 3 for the cross-user mutation attempt pattern and IDOR ownership check structure.

### 6.5 Adding a Testcontainers PostgreSQL migration test

TBD — see §3 Phase 4 for the Flyway + Testcontainers + Spring Boot 4.x setup pattern.

### 6.6 Per-rollout-phase notes

(Filled in as phases ship.)

---

## 7. What We Deliberately Don't Test

Exclusions agreed during the rollout (Phase 2 interview, Q5).

- **StubMoodClassifier** — it is a test double, not product logic; testing it adds no signal about the AI classification path. Re-evaluate if the stub grows complex enough to have its own behaviour contract. (Source: Phase 2 interview Q5.)
- **Thymeleaf template pixel-level layout** — visual snapshot tests on Thymeleaf templates break on minor whitespace changes and catch nothing about user-visible regressions. Re-evaluate if a deterministic visual diff tool with a stable baseline becomes available. (Source: Phase 2 interview Q5.)
- **Random number endpoint (`/v1/random`)** — demo leftover from project bootstrap; not product logic; existing smoke test in `ApplicationTests` is sufficient. Re-evaluate if the endpoint is repurposed for product use. (Source: Phase 2 interview Q5.)

---

## 8. Freshness Ledger

- Strategy (§1–§5) last reviewed: 2026-06-12
- Stack versions last verified: 2026-06-12
- AI-native tool references last verified: 2026-06-12

Refresh (`/10x-test-plan-spring --refresh`) when:

- a new top-3 risk surfaces from the roadmap or archive,
- a recommended tool's `checked:` date is older than three months,
- the project's tech stack changes (new framework, new test runner),
- §7 negative-space no longer matches what the team believes.

# Test Plan

> Phased test rollout for this project. Strategy is frozen at the top
> (§1–§5); cookbook patterns at the bottom (§6) fill in as phases ship.
> Read before writing any new test.
>
> Refresh: re-run `/10x-test-plan-spring --refresh` when stale (see §8).
>
> Last updated: 2026-06-15 (Phases 1, 2, and 3 complete; Phase 4 next)

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
| 6 | Flyway V1–V4 migration SQL compatible with H2 but invalid against PostgreSQL in prod → app fails to start; schema state unknown | High | Medium | Tech-stack: H2 in-memory for all tests, PostgreSQL in production; infrastructure.md; pom.xml |

### Risk Response Guidance

| Risk | What would prove protection | Must challenge | Context `/10x-research-spring` must ground | Likely cheapest layer | Anti-pattern to avoid |
|---|---|---|---|---|---|
| #1 | Entry is persisted with an unknown mood tag and the user sees the saved entry when the classifier throws or times out; no data is lost | "classifier exception = entry not saved is the correct behavior" (current service behavior may contradict PRD NFR; research must verify the intended fallback path) | JournalEntryService save/classify flow; how a timeout is distinguished from a structured failure; whether a fallback unknown-mood path exists or needs to be added | MockMvc integration test with mocked classifier throwing a timeout-style exception | Asserting 0L entries on classifier failure as "passing"; happy-path-only without testing timeout/exception branches |
| #2 | A response with a missing or null required field, or a score outside 0–100, is caught and mapped to `MoodClassificationFailedException` with `INVALID_RESPONSE` reason without crashing the application | "the happy path works means all parsing paths work"; "tests that mock the classifier cover actual parsing logic" | OpenAiMoodClassifier parsing: which fields are mandatory; score range validation; what a realistic malformed or partial AI response looks like | Unit test on OpenAiMoodClassifier with realistic partial/missing/out-of-range response shapes | Implementation mirror (expected values copied from parsing logic); not covering null/empty content or out-of-bounds score fields |
| #3 | An entry created at 22:05 UTC (00:05 Europe/Warsaw) is attributed to the *next* Warsaw calendar day; 7-day and 30-day windows align to completed Warsaw days, not UTC days | "fixed-clock tests using UTC noon timestamps cover all boundary cases" | JournalEntryService getTrendView; how Europe/Warsaw day boundaries are computed for each Instant; which existing Clock injection points enable precise boundary testing | Unit test (service) with Instant values carefully placed around the Warsaw midnight boundary | Using only UTC-noon timestamps that never probe the boundary; asserting entry counts without asserting which specific day a boundary entry belongs to |
| #4 | PUT/PATCH/DELETE on an entry ID belonging to user B returns 403 or 404 when user A is authenticated and makes the request | "isAuthenticated() implies authorized to mutate this specific resource"; "repository scoping on reads implies the same protection on writes" | JournalController edit/delete/override endpoints (once built); where ownership is checked relative to retrieval; whether the error response is 403 (forbidden) or 404 (not found) | MockMvc test with `.with(user(...))` on a cross-user mutation attempt | Testing only same-user happy path; not testing what happens when the entryId belongs to a different authenticated user |
| #5 | Anonymous GET /journal, /journal/history, and /journal/trends each redirect to /login; any new route addition does not bypass the deny-all fallback | "testing /journal covers /journal/history and /journal/trends"; "a route not in the public list is implicitly denied" | SecurityConfiguration route list; how the deny-all fallback is configured; which routes are explicitly public vs. protected | MockMvc anonymous GET on each distinct protected route pattern | Testing only one representative protected path; assuming sibling routes share the same protection without asserting it |
| #6 | Flyway V1–V4 migrations apply cleanly against a real PostgreSQL instance; schema is queryable after all migrations run; no H2-specific SQL present | "H2 tests pass means PostgreSQL migrations work"; "SERIAL vs IDENTITY and VARCHAR(n) are fully compatible between H2 and PostgreSQL" | Migration SQL contents; which PostgreSQL-specific or H2-specific constructs are present; Testcontainers availability and setup for Spring Boot 4.x | `@SpringBootTest` with a Testcontainers PostgreSQL profile | Skipping because H2 tests pass; not asserting post-migration schema shape |

---

## 3. Phased Rollout

Each row is a discrete rollout phase that will open its own change folder
via `/10x-new`. Status moves left-to-right through the values below; the
orchestrator updates Status as artifacts appear on disk.

| # | Phase name | Goal (one line) | Risks covered | Test types | Status | Change folder |
|---|---|---|---|---|---|---|
| 1 | AI boundary hardening | Prove entry durability on AI failure and verify classifier response contract | #1, #2 | unit + integration | complete | testing-ai-boundary-hardening |
| 2 | Trends time-zone accuracy | Prove mood trend calculations are correct across Europe/Warsaw midnight boundaries | #3 | integration (service + MockMvc) | complete | trends-time-zone-accuracy |
| 3 | Ownership + security enforcement | Ensure S-04 edit/delete/override verifies per-resource ownership; prevent security config regressions | #4, #5 | integration (MockMvc + spring-security-test) | complete | testing-ownership-security-enforcement |
| 4 | Migration safety gate | Verify Flyway migrations apply cleanly against real PostgreSQL via Testcontainers | #6 | integration (Testcontainers) | complete | testing-migration-safety-gate |

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

Use `@SpringBootTest` with `MockMvcBuilders.webAppContextSetup(...)` and `springSecurity()` for all tests that exercise routing, authentication, or security filters.

**Setup pattern** (copy from `SecurityRouteProtectionTests` or `AuthenticationFlowTests`):

```java
@SpringBootTest
class MyTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
    }
}
```

**Request post-processors:**

- Authenticated request: `.with(user("user@example.com").roles("USER"))` — injects a Spring Security `UserDetails` into the filter chain without a real login.
- CSRF-required request (POST/DELETE/PATCH): add `.with(csrf())` — supplies a valid CSRF token so the CSRF filter passes through to the authentication filter. Without it, a state-mutating anonymous request returns 403 (CSRF rejection) before the auth check runs.

**Live examples:**
- Anonymous route protection: `src/test/java/com/amadeuszx/moodlog/security/SecurityRouteProtectionTests.java`
- Full login-flow coverage: `src/test/java/com/amadeuszx/moodlog/user/AuthenticationFlowTests.java`

### 6.3 Adding a trend boundary test

TBD — see §3 Phase 2 for the Europe/Warsaw midnight boundary pattern and `FixedClockConfiguration` usage.

### 6.4 Adding an ownership verification test for a write endpoint

**The IDOR trap:** `JpaRepository.findById(UUID id)` is unscoped. It returns any entry regardless of `userAccountId`. A write endpoint that uses `findById` before mutating lets authenticated user A supply user B's entry UUID and silently succeed.

**The correct pattern:** All write endpoints must use a scoped query that includes the authenticated user's ID as a predicate:

```java
// Add to JournalEntryRepository:
Optional<JournalEntry> findByIdAndUserAccountId(UUID entryId, UUID userAccountId);
```

When the entry does not belong to the authenticated user, `findByIdAndUserAccountId` returns `Optional.empty()`. The controller maps this to 404 (not 403) — revealing that a resource exists at all is itself an information leak, so 404 is the correct response regardless of whether the entry exists under a different owner.

**Verification:** `JournalEntryOwnershipTests` (`src/test/java/com/amadeuszx/moodlog/journal/JournalEntryOwnershipTests.java`) contains four `@Disabled` stubs documenting this contract. When S-04 ships write endpoints, remove `@Disabled("Activate when S-04 edit/delete endpoints ship")` from all four methods and confirm the tests pass. The stubs assert the correct 404-for-cross-user and 3xx-for-owner contract — do not weaken them to 403 or 200.

### 6.5 Adding a Testcontainers PostgreSQL migration test

Use this pattern when you need to verify Flyway migration correctness against real PostgreSQL (not H2). The gate is a standalone `@SpringBootTest` that starts a `postgres:16` container once per test class and redirects the datasource via `@DynamicPropertySource`.

**Dependency note (Spring Boot 4.x + Testcontainers 2.x):** Add three test-scoped entries to `pom.xml`. Spring Boot 4.x manages the Testcontainers BOM, so no `<version>` is needed. Note that Testcontainers 2.x renamed artifacts — use the `testcontainers-*` prefix:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

**Class-level setup pattern:**

```java
@SpringBootTest
@Testcontainers
class FlywayMigrationPostgresTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configurePostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

Key rules:
- `@Testcontainers` (JUnit 5 extension) is required on the class — without it, `@Container` fields are not processed and the container never starts.
- The `@Container` field must be `static` so the container is shared across all test methods in the class (started once per class, not per test).
- `@DynamicPropertySource` overrides file-based properties at the highest Spring Environment precedence — it redirects the datasource without touching `src/test/resources/application.properties`. All other properties (AI disabled, Hibernate `validate`, journal limits) carry through unchanged.
- `@BeforeEach` cleanup: call `journalEntryRepository.deleteAll()` then `userAccountRepository.deleteAll()` (FK order) — same pattern as all other `@SpringBootTest` tests in this codebase.

**Migration count assertion:**

```java
@Autowired
private Flyway flyway;

@Test
@DisplayName("all Flyway migrations apply cleanly against PostgreSQL")
void allMigrationsApplyCleanlyToPostgres() {
    val applied = Arrays.stream(flyway.info().all())
        .filter(info -> info.getState() == MigrationState.SUCCESS)
        .count();
    assertEquals(4L, applied);  // update this to 5L, 6L, etc. when new migrations ship
}
```

Assert the **exact** count, not a floor (`>= N`). Flyway silently skips migrations whose SQL files are missing from the classpath if their checksum is already in `flyway_schema_history`. A context-load-only test passes in that scenario; the exact-count assertion catches a missing file. Update the expected count whenever a new migration is added.

**Live example:** `src/test/java/com/amadeuszx/moodlog/migration/FlywayMigrationPostgresTests.java`

### 6.6 Per-rollout-phase notes

#### Phase 3

Phase 3 delivered two new test artifacts:

- **`SecurityRouteProtectionTests`** (`src/test/java/com/amadeuszx/moodlog/security/SecurityRouteProtectionTests.java`) — 5 anonymous-request tests forming a standalone security regression fence. Asserts that all three journal GET routes and `POST /journal` redirect anonymous requests to `/login`, and that an unmapped path under `/journal` is still blocked by the `anyRequest().authenticated()` deny-all fallback.

- **`JournalEntryOwnershipTests`** (`src/test/java/com/amadeuszx/moodlog/journal/JournalEntryOwnershipTests.java`) — 4 `@Disabled` ownership contract stubs for S-04 (edit/delete/mood-override endpoints). These tests compile and appear as skipped until S-04 ships. To activate: remove `@Disabled("Activate when S-04 edit/delete endpoints ship")` from all four methods and add `findByIdAndUserAccountId` to `JournalEntryRepository` (see §6.4).

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

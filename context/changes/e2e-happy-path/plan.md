# E2E Happy-Path Playwright Tests — Implementation Plan

## Overview

Wire the existing Playwright 1.60.0 dependency into the project's `@SpringBootTest` test infrastructure and implement five ordered happy-path scenarios in a single test class. The existing test profile (H2 in-memory, AI disabled via `StubMoodClassifier`) covers E2E needs without a new Spring profile.

## Current State Analysis

Playwright 1.60.0 is declared in `pom.xml` but with `compile` scope (should be `test`). No Maven Failsafe plugin exists. No `spring-boot-maven-plugin` start/stop executions exist. No E2E test files exist anywhere under `src/test/`. The web surface, auth flow, and form selectors are fully mapped from research.

## Desired End State

Running `.\mvnw.cmd test -Dgroups=e2e` executes five browser-driven scenarios against a real embedded Tomcat (random port) and passes green. Running `.\mvnw.cmd test` (no flags) continues to run only fast unit and integration tests, with E2E excluded. The five scenarios covered: register → /journal, create entry → appears in list, history view, trends view, logout + login again.

### Key Discoveries

- `pom.xml:124-128` — Playwright dependency scope is `compile`, must change to `test`
- `pom.xml:145-148` — `spring-boot-maven-plugin` has no executions — correct, since we're using `@SpringBootTest(RANDOM_PORT)` not Failsafe
- `SecurityConfiguration.java:59` — username parameter is `email`, not the Spring Security default `username`
- `src/main/java/com/amadeuszx/moodlog/classification/StubMoodClassifier.java` — active production bean when `moodlog.ai.enabled=false`; always succeeds; keyword-free English content maps to `NEUTRAL` (score 55)
- `src/test/resources/application.properties` — disables AI (`moodlog.ai.enabled=false`), uses H2; this profile is automatically active in `@SpringBootTest` tests
- `src/test/java/com/amadeuszx/moodlog/user/AuthenticationFlowTests.java:76-264` — pattern: `@BeforeEach` deletes all users and entries; Playwright tests follow same cleanup convention

## What We're NOT Doing

- Not adding Maven Failsafe plugin or `spring-boot-maven-plugin` start/stop — `@SpringBootTest(RANDOM_PORT)` handles the lifecycle
- Not creating a dedicated `application-e2e.properties` — existing test profile is sufficient
- Not testing error flows (invalid email, wrong password, duplicate registration) — those are covered by `AuthenticationFlowTests`
- Not asserting mood tag after entry creation — mood classification correctness is covered by Phase 1 unit tests
- Not wiring to CI — local only
- Not testing DELETE, PUT, PATCH operations on journal entries — those are S-04 flows with ownership tests; out of happy-path scope

## Implementation Approach

Use `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` with `@TestInstance(TestInstance.Lifecycle.PER_CLASS)`. The PER_CLASS lifecycle makes `@BeforeAll` non-static, which is required for `@LocalServerPort` (an instance field) to be injected before the browser launches. One `BrowserContext` is created and shared across all five ordered test methods, preserving cookies and session state between scenarios. `maven-surefire-plugin` is configured to exclude `@Tag("e2e")` from the default `mvnw test` run.

## Critical Implementation Details

**`@TestInstance(PER_CLASS)` is not optional.** Without it, `@BeforeAll` must be static, but `@LocalServerPort` injects into instance fields — the port value would be 0 when the browser launches. `PER_CLASS` makes `@BeforeAll` non-static and allows `@LocalServerPort` to be injected before `launchBrowser()` runs.

**Playwright browser binaries must be installed once on the developer machine.** The Java API wraps native browser executables. Run this once before the first E2E test run:
```
.\mvnw.cmd exec:java "-Dexec.classpathScope=test" "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"
```
Without this step, `Playwright.create()` throws at runtime.

**DB cleanup must happen in `@BeforeAll`, not `@BeforeEach`.** The five test methods share state (the registered user, the created entry). Cleaning between methods would break the ordered journey. Clean once before all methods start.

---

## Phase 1: Maven Build Wiring

### Overview

Fix the Playwright dependency scope and configure Surefire to exclude `@Tag("e2e")` from the default test run. After this phase, `.\mvnw.cmd test` runs fast tests only and the project compiles with Playwright correctly scoped.

### Changes Required

#### 1. Fix Playwright dependency scope

**File**: `pom.xml`

**Intent**: Change the Playwright dependency scope from `compile` (default) to `test` so the Playwright JARs are excluded from the production artifact.

**Contract**: In the existing `<dependency>` block for `com.microsoft.playwright:playwright:1.60.0`, add `<scope>test</scope>` as a child element.

#### 2. Add maven-surefire-plugin configuration

**File**: `pom.xml`

**Intent**: Exclude tests tagged `@Tag("e2e")` from the default Surefire run so `.\mvnw.cmd test` stays fast.

**Contract**: Add a `maven-surefire-plugin` entry inside `<build><plugins>` with `<excludedGroups>e2e</excludedGroups>` in its `<configuration>`. No version needed — Spring Boot 4.x BOM manages it.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <excludedGroups>e2e</excludedGroups>
    </configuration>
</plugin>
```

### Success Criteria

#### Automated Verification

- `.\mvnw.cmd test` passes (fast tests only; E2E excluded, no compilation errors)
- `.\mvnw.cmd test -Dgroups=e2e` exits with failure "No tests found" (expected — no test class yet)
- `.\mvnw.cmd package -DskipTests` produces a JAR that does not include `playwright` classes in `BOOT-INF/lib` at test-only scope

#### Manual Verification

- Playwright browser binaries installed: run `.\mvnw.cmd exec:java "-Dexec.classpathScope=test" "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"` once on the dev machine; confirm it exits 0 and downloads Chromium

**Implementation Note**: After automated verification passes, run the browser binary install step manually and confirm success before proceeding to Phase 2.

---

## Phase 2: E2E Test Class

### Overview

Create the single `JournalHappyPathE2ETests` class with five ordered scenarios covering the full user journey: register, create entry, view history, view trends, logout + login.

### Changes Required

#### 1. Create the E2E test class

**File**: `src/test/java/com/amadeuszx/moodlog/e2e/JournalHappyPathE2ETests.java`

**Intent**: A single `@SpringBootTest` test class with five ordered browser-driven scenarios sharing one Playwright `BrowserContext` and one test user.

**Contract**: The class requires these annotations and lifecycle:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("e2e")
class JournalHappyPathE2ETests {

    @LocalServerPort
    private int port;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeAll
    void setUp() {
        journalEntryRepository.deleteAll();
        userAccountRepository.deleteAll();
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterAll
    void tearDown() {
        browser.close();
        playwright.close();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
```

#### 2. Scenario 1 — Register lands on journal

**File**: `src/test/java/com/amadeuszx/moodlog/e2e/JournalHappyPathE2ETests.java`

**Intent**: Verify that a new user can register and is automatically authenticated and redirected to the journal page.

**Contract**: Method annotated `@Test @Order(1)`. Navigate to `/register`, fill `#email` with `"e2e@example.com"` and `#password` with `"e2e-password"`, click the submit button. Assert URL ends with `/journal` and page body contains `"Twój prywatny dziennik"`.

#### 3. Scenario 2 — Create entry appears in list

**File**: `src/test/java/com/amadeuszx/moodlog/e2e/JournalHappyPathE2ETests.java`

**Intent**: Verify that an authenticated user can create a journal entry and the entry excerpt appears in the recent entries list on the journal page.

**Contract**: Method annotated `@Test @Order(2)`. Navigate to `/journal`. Fill `#content` with a fixed test string (e.g., `"E2E happy path test entry."`). Click the submit button (text: `"Zapisz wpis"`). Assert URL ends with `/journal` and the page contains the test string.

#### 4. Scenario 3 — History shows created entry

**File**: `src/test/java/com/amadeuszx/moodlog/e2e/JournalHappyPathE2ETests.java`

**Intent**: Verify that the entry created in Scenario 2 is visible on the journal history page.

**Contract**: Method annotated `@Test @Order(3)`. Navigate to `/journal/history`. Assert URL contains `/journal/history` and page contains the same test entry string.

#### 5. Scenario 4 — Trends page loads with chart canvas

**File**: `src/test/java/com/amadeuszx/moodlog/e2e/JournalHappyPathE2ETests.java`

**Intent**: Verify that the mood trends page loads, displays the page heading, and renders the chart canvas elements.

**Contract**: Method annotated `@Test @Order(4)`. Navigate to `/journal/trends`. Assert URL contains `/journal/trends`, page contains `"Trendy nastroju"`, and `#seven-day-trend-chart` canvas element is visible.

#### 6. Scenario 5 — Logout and login again

**File**: `src/test/java/com/amadeuszx/moodlog/e2e/JournalHappyPathE2ETests.java`

**Intent**: Verify the logout flow clears the session and the login flow re-authenticates the user and redirects to the journal.

**Contract**: Method annotated `@Test @Order(5)`. Click the logout button on the current page (a form submit `POST /logout`). Assert URL contains `/login`. Navigate to `/login` (or assert already there), fill `input[name="email"]` with `"e2e@example.com"` and `input[name="password"]` with `"e2e-password"`. Click submit (text: `"Zaloguj się"`). Assert URL ends with `/journal`.

**Note**: The username input name is `email` (not the Spring Security default `username`) — confirmed in `SecurityConfiguration.java:59`.

### Success Criteria

#### Automated Verification

- `.\mvnw.cmd test -Dgroups=e2e` passes with all 5 test methods green
- `.\mvnw.cmd test` (no flags) continues to pass with E2E excluded (no regression on existing test suite)

#### Manual Verification

- Re-run `.\mvnw.cmd test -Dgroups=e2e` with Playwright headed mode (`playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false))`) and visually confirm each browser scenario executes correctly
- Verify Scenario 2 entry appears in Scenario 3 history list (correct shared-state flow)
- Verify Scenario 5 logout fully clears session (directly navigating to `/journal` after logout redirects to `/login`)

**Implementation Note**: After all automated tests pass, do one headed run to visually verify the browser flows match expected behavior before marking this phase complete.

---

## Testing Strategy

### No additional unit tests required

Phase 1 (AI boundary hardening) already covers classifier correctness. Phase 3 (ownership + security) covers auth redirects. These E2E tests are the testing artifact.

### Running E2E tests

```
# Run E2E tests only (headed — opens browser window)
.\mvnw.cmd test -Dgroups=e2e

# Run E2E tests headless (CI-style)
.\mvnw.cmd test -Dgroups=e2e

# Run fast tests only (excludes e2e by Surefire config)
.\mvnw.cmd test
```

To run headed, temporarily change `playwright.chromium().launch()` to `playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false))` in `@BeforeAll` for debugging.

### Manual Testing Steps

1. Install Playwright browser binaries (one-time): `.\mvnw.cmd exec:java "-Dexec.classpathScope=test" "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"`
2. Run `.\mvnw.cmd test -Dgroups=e2e` and confirm 5 tests pass
3. Run `.\mvnw.cmd test` (no flags) and confirm E2E is excluded and all fast tests still pass
4. For visual confirmation: run with `setHeadless(false)` and watch the browser navigate each scenario

## Performance Considerations

`@SpringBootTest(RANDOM_PORT)` starts the embedded Tomcat once per test class. With PER_CLASS lifecycle, Spring may cache the context across other test classes with the same configuration. The H2 in-memory DB cleanup in `@BeforeAll` ensures E2E always starts with a clean state regardless of context caching. Total E2E run time expected: ~15-30 seconds.

## Migration Notes

No schema migrations. No data migrations. No changes to production code.

## References

- Research: `context/changes/e2e-happy-path/research.md`
- Auth flow tests pattern: `src/test/java/com/amadeuszx/moodlog/user/AuthenticationFlowTests.java`
- Security config (username param): `src/main/java/com/amadeuszx/moodlog/user/SecurityConfiguration.java:59`
- StubMoodClassifier: `src/main/java/com/amadeuszx/moodlog/classification/StubMoodClassifier.java`
- Playwright Java API docs: https://playwright.dev/java/docs/intro

---

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Maven Build Wiring

#### Automated

- [x] 1.1 `.\mvnw.cmd test` passes with E2E excluded and no compilation errors
- [x] 1.2 `.\mvnw.cmd test -Dgroups=e2e` exits with "No tests found" (before Phase 2)
- [x] 1.3 `.\mvnw.cmd package -DskipTests` produces JAR without Playwright in production classpath

#### Manual

- [x] 1.4 Playwright Chromium browser binaries installed successfully on dev machine

### Phase 2: E2E Test Class

#### Automated

- [ ] 2.1 `.\mvnw.cmd test -Dgroups=e2e` passes with all 5 test methods green
- [ ] 2.2 `.\mvnw.cmd test` (no flags) passes with E2E excluded and no regression on existing suite

#### Manual

- [ ] 2.3 Headed browser run visually confirms all 5 scenarios execute correctly
- [ ] 2.4 Scenario 2 entry is visible in Scenario 3 history list (shared state verified)
- [ ] 2.5 Scenario 5 logout fully clears session (post-logout navigation to `/journal` redirects to `/login`)

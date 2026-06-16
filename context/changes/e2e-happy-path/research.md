---
date: 2026-06-15T00:00:00+02:00
researcher: m.olszewski
git_commit: f0e56079eed200b6635d22ca99cc5aa5ad6884b5
branch: master
repository: moodlog
topic: "E2E happy-path Playwright tests: scope, infrastructure, and flows"
tags: [research, e2e, playwright, happy-path, auth, journal, trends]
status: complete
last_updated: 2026-06-15
last_updated_by: m.olszewski
---

# Research: E2E Happy-Path Playwright Tests

**Date**: 2026-06-15  
**Researcher**: m.olszewski  
**Git Commit**: f0e56079eed200b6635d22ca99cc5aa5ad6884b5  
**Branch**: master  
**Repository**: moodlog

## Research Question

Scope and implement E2E happy-path Playwright tests covering: register + login, journal entry creation, journal history view, and mood trends view.

## Summary

All four product slices (S-01–S-04) are shipping-complete. Playwright 1.60.0 is already in `pom.xml` (wrong scope — needs `<scope>test</scope>`). Zero Maven infrastructure exists for integration-test lifecycle: no Failsafe plugin, no spring-boot-maven-plugin start/stop executions. No E2E tests exist anywhere. The happy-path flows are fully mapped — routes, form selectors, redirects — and can be driven straight from the Playwright Java API in JUnit 5. CSRF is live and handled automatically by the real browser; the only auth quirk is that the username field is named `email`, not the Spring Security default `username`.

---

## Detailed Findings

### 1. App URL Surface

Base URL: `http://localhost:8080` (default; no `server.port` or context-path override).  
Session cookie: `MOODLOGSESSION` (30-min timeout).

| Route | Method | Controller:line | Template | Notes |
|---|---|---|---|---|
| `/` | GET | `IndexController.java:9` | `index.html` | Public landing |
| `/register` | GET | `AuthController.java:68` | `register.html` | Public |
| `/register` | POST | `AuthController.java:73` | redirect `/journal` on success | Auto-authenticates on success |
| `/login` | GET | `AuthController.java:52` | `login.html` | Public |
| `/login` | POST | Spring Security | redirect `/journal` or saved-request | Handled by security filter |
| `/logout` | POST | Spring Security | redirect `/login?logout` | CSRF required |
| `/journal` | GET | `JournalController.java:46` | `journal.html` | Auth required |
| `/journal` | POST | `JournalController.java:59` | redirect `/journal` on success | Auth required |
| `/journal/history` | GET | `JournalController.java:84` | `journal-history.html` | Auth required |
| `/journal/trends` | GET | `JournalController.java:140` | `journal-trends.html` | Auth required |
| `/journal/{id}/edit` | GET | `JournalController.java:115` | `journal-edit.html` | Auth required |
| `/journal/{id}` | PUT | `JournalController.java:124` | redirect `/journal/history` | Hidden `_method=PUT` |
| `/journal/{id}` | DELETE | `JournalController.java:103` | redirect `/journal/history` | Hidden `_method=DELETE` |
| `/journal/{id}/mood` | PATCH | `JournalController.java:109` | redirect `/journal/history` | Hidden `_method=PATCH` |

Source: `src/main/java/com/amadeuszx/moodlog/user/AuthController.java`, `src/main/java/com/amadeuszx/moodlog/journal/JournalController.java`

---

### 2. Form Selectors (Playwright-Ready)

#### Registration (`/register`)
- Email: `#email` / `input[name="email"]` (type: email)
- Password: `#password` / `input[name="password"]` (type: password; min 6 chars)
- Timezone: hidden, auto-filled by `register.js` via `Intl.DateTimeFormat` — Playwright browser handles this automatically
- Submit: `button[type="submit"]` text: "Załóż konto"
- CSRF: `input[type="hidden"][name="_csrf"]` — browser submits automatically
- Success: redirect to `/journal`
- Error selectors: look for visible error text on `register.html` re-render

Source: `src/main/resources/templates/register.html:12,16,22,31`, `src/main/java/com/amadeuszx/moodlog/user/register/RegistrationForm.java`

#### Login (`/login`)
- **Username field name: `email`** — NOT Spring Security default `username` (configured at `SecurityConfiguration.java:59`)
- Email: `#email` / `input[name="email"]`
- Password: `#password` / `input[name="password"]`
- Submit: `button[type="submit"]` text: "Zaloguj się"
- Success: redirect to `/journal` (or saved request)
- Failure: redirect to `/login?error`

Source: `src/main/resources/templates/login.html:15,20,25,28`, `src/main/java/com/amadeuszx/moodlog/user/SecurityConfiguration.java:59,76,88`

#### Journal Entry Creation (`/journal`)
- Content: `#content` / `textarea[name="content"]` (max 2000 chars)
- Submit: `button[type="submit"]` text: "Zapisz wpis"
- Success: redirect back to `/journal` (page reloads, entry appears in recent list)
- Navigation to history: `a[href="/journal/history"]` text: "Historia wpisów"
- Navigation to trends: `a[href="/journal/trends"]` text: "Trendy nastroju"

Source: `src/main/resources/templates/journal.html:23,27,34,38,17,19`

#### Journal History (`/journal/history`)
- Entry excerpt visible in list
- Delete form: `form` with hidden `_method=DELETE` → button text "Usuń"
- Mood override select: `select[name="moodTag"]`
- Mood update button: "Popraw nastrój"
- Edit link: `a` text "Edytuj" → navigates to `/journal/{id}/edit`
- Pagination: "Nowsze wpisy" / "Starsze wpisy" links

Source: `src/main/resources/templates/journal-history.html:33-61`

#### Mood Trends (`/journal/trends`)
- Page heading: "Trendy nastroju"
- Chart canvases: `#seven-day-trend-chart`, `#thirty-day-trend-chart`, `#weekly-trend-chart`
- Charts rendered by `/js/journal-trends.js` using `window.journalTrendsData`
- Current-week summary section present with entry counts and dominant mood

Source: `src/main/resources/templates/journal-trends.html:11,25,48,59,70,54,65,76`

---

### 3. Security & Auth Constraints for Playwright

| Concern | Value | Impact on E2E |
|---|---|---|
| CSRF | Enabled | No impact — real browser auto-submits CSRF token from hidden input |
| Username param | `email` (not `username`) | Use `input[name="email"]` not `input[name="username"]` |
| Session cookie | `MOODLOGSESSION` | Playwright browser context persists it automatically |
| Session rotation | `ChangeSessionIdAuthenticationStrategy` — ID changes on login | No impact — Playwright tracks cookies transparently |
| Protected routes | All `/journal/**` require auth | Tests must log in first |
| Method override | `HiddenHttpMethodFilter` enabled (`application.properties:13`) | DELETE/PATCH/PUT via POST + `_method` hidden field; Playwright submits forms normally |

Source: `src/main/java/com/amadeuszx/moodlog/user/SecurityConfiguration.java:39-76,123,128`

**Test user seeding**: No pre-seeded users. Each test run must register a fresh user via `/register`. Recommended: generate unique email per test run (`testuser-{timestamp}@example.com`).

---

### 4. Playwright Infrastructure Gap (pom.xml)

Current state of `pom.xml`:

```xml
<!-- Playwright dependency — wrong scope -->
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.60.0</version>
    <!-- scope defaults to compile — should be test -->
</dependency>
```

Missing entirely:
- `maven-failsafe-plugin` (runs `*IT.java` / `*E2ETests.java` in `integration-test` phase)
- `spring-boot-maven-plugin` `start` execution in `pre-integration-test`
- `spring-boot-maven-plugin` `stop` execution in `post-integration-test`
- No `application-e2e.properties` test profile
- No existing E2E test files anywhere under `src/test/`

Source: `pom.xml:124-128,145-148`

**What the plan must add:**

```xml
<!-- 1. Fix scope on existing Playwright dependency -->
<scope>test</scope>

<!-- 2. Failsafe plugin — runs integration tests -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <includes>
            <include>**/*E2ETests.java</include>
        </includes>
    </configuration>
</plugin>

<!-- 3. spring-boot-maven-plugin — start/stop app around E2E -->
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>pre-integration-test</id>
            <goals><goal>start</goal></goals>
        </execution>
        <execution>
            <id>post-integration-test</id>
            <goals><goal>stop</goal></goals>
        </execution>
    </executions>
</plugin>
```

The `spring-boot-maven-plugin` `start` goal forks a separate JVM running the app during `pre-integration-test`. `stop` kills it after. Failsafe picks up `*E2ETests.java` in `integration-test` phase.

---

### 5. AI Classifier Behavior in E2E Context

When the app runs for E2E (not the test H2 profile), `moodlog.ai.enabled` is `true` by default and the real OpenAI classifier fires on journal entry creation. This has two implications:
- Tests must either use a real API key (fragile, slow, costly) or set `moodlog.ai.enabled=false` via a Spring profile activated during the `start` goal
- The happy-path should not assert on the specific mood tag, only that an entry was saved and appears in history

Recommendation: activate an `e2e` Spring profile during `start` that disables AI (`moodlog.ai.enabled=false`), using the existing `StubMoodClassifier` fallback path.

Source: `src/test/resources/application.properties` (test profile disables AI), `context/foundation/test-plan.md:§7`

---

### 6. Happy-Path Flow Sequence

For each Playwright test scenario, the minimal sequence is:

**Scenario A — Register & land on journal**
1. `GET /register` → fill `#email`, `#password` → click submit
2. Assert: URL becomes `/journal`, page shows "Twój prywatny dziennik"

**Scenario B — Login (existing user)**
1. Register user (setup)
2. Navigate to `/login` → fill `#email`, `#password` → click submit
3. Assert: URL becomes `/journal`

**Scenario C — Create journal entry**
1. Login (setup)
2. `GET /journal` → fill `#content` with text → click "Zapisz wpis"
3. Assert: URL returns to `/journal`, entry excerpt visible in recent entries list

**Scenario D — View journal history**
1. Login + create entry (setup)
2. Click "Historia wpisów" link → navigate to `/journal/history`
3. Assert: entry excerpt visible on history page

**Scenario E — View mood trends**
1. Login + create entry (setup)
2. Click "Trendy nastroju" link → navigate to `/journal/trends`
3. Assert: page heading "Trendy nastroju" visible, canvas elements present

---

## Code References

- `src/main/java/com/amadeuszx/moodlog/user/AuthController.java:52-102` — login GET, register GET/POST, redirect flows
- `src/main/java/com/amadeuszx/moodlog/user/SecurityConfiguration.java:39-76` — route permissions, username param, success/failure URLs
- `src/main/java/com/amadeuszx/moodlog/journal/JournalController.java:46-140` — all journal routes
- `src/main/resources/templates/register.html:12-35` — registration form structure
- `src/main/resources/templates/login.html:15-32` — login form structure
- `src/main/resources/templates/journal.html:17-56` — journal entry form and navigation
- `src/main/resources/templates/journal-history.html:33-61` — entry list, delete, mood override
- `src/main/resources/templates/journal-trends.html:11-78` — trends page, chart canvases
- `pom.xml:124-128` — Playwright dependency (scope fix needed)
- `pom.xml:145-148` — spring-boot-maven-plugin (executions missing)
- `src/test/java/com/amadeuszx/moodlog/user/AuthenticationFlowTests.java:76-264` — MockMvc login patterns (analogous to E2E flows)
- `src/main/resources/application.properties:13` — `spring.mvc.hiddenmethod.filter.enabled=true`

## Architecture Insights

**CSRF is not an E2E obstacle.** Real browser context (Playwright Chromium) parses the HTML and includes the hidden CSRF token on form submission without any extra test code. This differs from MockMvc tests which require `.with(csrf())`.

**Username field named `email` is the key gotcha.** Spring Security's default `usernameParameter` is `username`. This app overrides it to `email` (`SecurityConfiguration.java:59`). Any Playwright locator using `input[name="username"]` will silently fail; must use `input[name="email"]`.

**Method override requires form submission, not direct PATCH/PUT/DELETE.** Playwright should submit forms naturally rather than calling `page.request.delete(...)` for delete/update flows, since the server uses `HiddenHttpMethodFilter` and expects POST with `_method` override from browser forms.

**App must be started externally to Playwright tests.** The Playwright Java API does not start the Spring Boot app; Maven Failsafe + spring-boot-maven-plugin `start`/`stop` goals own the lifecycle. E2E tests should read the base URL from a system property set by the plugin (e.g., `-Dapp.base-url=http://localhost:8080`).

**AI must be stubbed for E2E.** Running E2E against live AI adds cost and flakiness. A Spring profile (`e2e`) that sets `moodlog.ai.enabled=false` should be activated via the `start` goal's `-Dspring.profiles.active=e2e` argument.

## Historical Context (from prior changes)

- `context/foundation/test-plan.md:95` — "Playwright 1.60.0 (in pom.xml) Dependency present but no E2E tests written; not required by any current rollout phase"
- `context/foundation/test-plan.md:117` — "E2E on critical flows | CI on PR | optional — not in current rollout"
- `context/foundation/test-plan.md:§1` — Cost × signal principle: "Do not promote to e2e because e2e 'feels safer.'" — E2E here is justified because it catches full browser-stack regressions across the completed product that integration tests cannot reach
- `context/foundation/shape-notes.md:171` — Early seed note mentioned "at least one end-to-end or integration-style test for the main journaling flow with AI mocked" — this change fulfills that intent
- `context/foundation/roadmap.md:30-36` — S-01 through S-04 all complete as of 2026-06-15; E2E tests are a post-roadmap quality gate

## Related Research

No prior E2E research artifacts exist. This is the first E2E research document for this project.

## Open Questions

1. **E2E Spring profile**: Does a `src/main/resources/application-e2e.properties` need to exist, or can AI be disabled by passing `-Dspring.profiles.active=test` to the `start` goal (which would reuse `src/test/resources/application.properties`)? The test profile uses H2 — likely undesirable for E2E which should mirror production (PostgreSQL). A dedicated `e2e` profile that disables only AI (keeping PostgreSQL or H2 with `MODE=PostgreSQL`) is the cleaner choice.

2. **Database for E2E**: Should E2E run against H2 (easy, fast) or a Testcontainers PostgreSQL (higher fidelity)? Given Flyway is already verified against PostgreSQL (Phase 4), H2 with PostgreSQL mode is acceptable for E2E happy-path.

3. **Port randomization**: Should E2E use a fixed port (8080) or random port (`server.port=0`)? Fixed port is simpler for Playwright base URL config; random port avoids conflicts on shared CI — but scope says local only, so fixed 8080 is fine.

4. **Test isolation**: Should each Playwright test scenario register its own unique user, or share a user across scenarios (ordered test execution)? Unique-per-test is safer and aligns with the JUnit 5 pattern used in existing tests; shared user risks ordering dependencies.

5. **Mood assertion after entry creation**: With AI disabled, what mood tag does the `StubMoodClassifier` return? Verify in `StubMoodClassifier` — the happy-path may need to assert on whatever stub value it returns, or simply assert that any mood tag is shown.

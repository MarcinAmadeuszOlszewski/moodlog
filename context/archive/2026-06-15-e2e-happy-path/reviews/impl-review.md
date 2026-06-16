<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: E2E Happy-Path Playwright Tests

- **Plan**: context/changes/e2e-happy-path/plan.md
- **Scope**: Phase 1 + Phase 2 of 2
- **Date**: 2026-06-15
- **Verdict**: NEEDS ATTENTION
- **Findings**: 1 critical  4 warnings  2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | FAIL |
| Success Criteria | PASS |

## Findings

### F1 — Missing @DisplayName on all five test methods

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/test/java/com/amadeuszx/moodlog/e2e/JournalHappyPathE2ETests.java:69,83,94,103,113
- **Detail**: AGENTS.md mandates `@DisplayName` on every test method. AuthenticationFlowTests (the plan's reference class) applies it on every `@Test`. All five methods in JournalHappyPathE2ETests are missing it — zero coverage.
- **Fix**: Add `@DisplayName` to each of the five methods with descriptive text: "registration redirects to the journal page", "created entry appears in the journal list", "history page lists the previously created entry", "trends page renders the seven-day chart canvas", "logout clears session and re-login restores journal access".
- **Decision**: FIXED — added @DisplayName to all 5 test methods

### F2 — Surefire plugin placed in Maven profile, not build/plugins

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: pom.xml (skip-e2e profile block)
- **Detail**: Plan required maven-surefire-plugin directly inside `<build><plugins>`. Actual wraps it in `<profile id="skip-e2e">` activated by `<property><name>!groups</name></property>`. Functionally equivalent (E2E excluded from default run, included via `-Dgroups=e2e`), but deviates from plan structure. Fast-test run confirmed passing (105/0/0).
- **Fix**: Accept the profile approach — it is functionally superior. Update plan Phase 1 as addendum.
- **Decision**: FIXED — plan.md Phase 1 updated with addendum noting profile-based mechanism

### F3 — Playwright process leak in tearDown (no try-finally)

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: src/test/java/com/amadeuszx/moodlog/e2e/JournalHappyPathE2ETests.java:59-63
- **Detail**: tearDown() calls `browser.close()` then `playwright.close()` sequentially. If `browser.close()` throws, `playwright.close()` is skipped — leaving an orphaned Playwright/Chrome subprocess. Over repeated runs this accumulates zombie processes.
- **Fix A ⭐ Recommended**: Wrap in try-finally so `playwright.close()` always runs.
  - Strength: Matches Java resource cleanup convention; no orphaned Chrome processes on test failure.
  - Tradeoff: 3-line change, null guards add minor verbosity.
  - Confidence: HIGH — standard lifecycle pattern; no downside.
  - Blind spot: context and page not explicitly closed, but browser.close() cascades to them.
- **Fix B**: Keep as-is (tests pass locally; risk is only on crash paths).
  - Strength: No change needed.
  - Tradeoff: Zombie process risk on test failure runs.
  - Confidence: LOW — known anti-pattern.
  - Blind spot: None.
- **Decision**: FIXED via Fix A — wrapped browser.close()/playwright.close() in try-finally with null guards

### F4 — Raw page.content().contains() instead of retrying assertions

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: src/test/java/com/amadeuszx/moodlog/e2e/JournalHappyPathE2ETests.java:78,89,98,107
- **Detail**: Four content assertions use `assertTrue(page.content().contains("..."))`. `page.content()` is a one-shot DOM snapshot. If target text is rendered after initial load it fails intermittently. The same class already uses `assertThat(page).hasURL()` (retrying) for URL checks — content checks should be consistent.
- **Fix A ⭐ Recommended**: Replace with `assertThat(page).containsText("...")`.
  - Strength: Playwright retries until timeout — resilient to async rendering; consistent with hasURL() already used.
  - Tradeoff: Import `com.microsoft.playwright.assertions.PlaywrightAssertions`.
  - Confidence: HIGH — Thymeleaf renders server-side so risk is low currently, but JS-driven content could evolve.
  - Blind spot: None significant; containsText does substring match.
- **Fix B**: Add explicit `page.waitForLoadState()` before each content check.
  - Strength: Minimal change; explicit about what it's waiting for.
  - Tradeoff: waitForLoadState("networkidle") is slow; doesn't retry for JS-rendered content.
  - Confidence: LOW — bandaid; one-shot snapshot root cause remains.
- **Decision**: FIXED via Fix A — replaced with `assertThat(page.locator("body")).containsText(...)` (verified `PageAssertions` in 1.60.0 jar has no `containsText`; `LocatorAssertions` does, so used `page.locator("body")`)

### F5 — Scenario 5 selectors and URL assertion differ from plan

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/test/java/com/amadeuszx/moodlog/e2e/JournalHappyPathE2ETests.java:113-124
- **Detail**: Plan specified four contracts for Scenario 5 that differ from actual: (1) logout: text click vs. form submit selector; (2) post-logout URL: `hasURL("/login?logout")` vs. `contains("/login")`; (3) login fields: `#email`/`#password` IDs vs. `input[name="email"]`/`input[name="password"]`; (4) login submit: generic `button[type='submit']` vs. text "Zaloguj się". Tests pass — this is plan text drift, not functional regression.
- **Fix**: Accept actual selectors as correct. Update plan Phase 2 Scenario 5 as addendum.
- **Decision**: FIXED — plan.md Phase 2 Scenario 5 updated with addendum noting actual selectors

### F6 — Class-level JUnit annotation order not alphabetical

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/test/java/com/amadeuszx/moodlog/e2e/JournalHappyPathE2ETests.java:25-28
- **Detail**: AGENTS.md requires alphabetical order within each annotation group. Current JUnit group order: `@TestInstance`, `@TestMethodOrder`, `@Tag`. Alphabetical: `@Tag`, `@TestInstance`, `@TestMethodOrder`.
- **Fix**: Reorder to `@SpringBootTest` / `@Tag` / `@TestInstance` / `@TestMethodOrder`.
- **Decision**: FIXED — annotations reordered alphabetically within JUnit group

### F7 — Playwright pom.xml dependency uses 4-space indent vs. tab

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: pom.xml:124-129
- **Detail**: Playwright dependency block indented with 4-spaces; surrounding dependencies use tabs.
- **Fix**: Reindent to tabs to match surrounding blocks.
- **Decision**: FIXED — reindented to tabs

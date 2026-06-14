<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Trends Time Zone Accuracy

- **Plan**: context/changes/trends-time-zone-accuracy/plan.md
- **Scope**: All Phases (1–4 of 4)
- **Date**: 2026-06-14
- **Verdict**: NEEDS ATTENTION (triaged — all findings resolved)
- **Findings**: 0 critical · 4 warnings · 6 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — Invalid timezone throws uncaught IllegalArgumentException → 500

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/amadeuszx/moodlog/user/UserAccountService.java:169
- **Detail**: `resolveTimezone()` throws `IllegalArgumentException` for a non-blank invalid timezone. `AuthController.register()` catches only `InvalidPasswordException` and `DuplicateUserAccountException` — a tampered hidden field or programmatic POST results in a 500. The plan said to propagate the exception but did not instruct the controller to catch it.
- **Fix A ⭐ Recommended**: In `resolveTimezone()`, catch `DateTimeException` on non-blank values too and fall back to `"Europe/Warsaw"` with a warning log — consistent with the blank-value branch.
  - Strength: Eliminates the 500; browser-supplied IANA values are always valid so the fallback only triggers on tampered input.
  - Tradeoff: Silently accepts garbage input. Acceptable since the field is not user-editable.
  - Confidence: HIGH — the blank-branch pattern is already in place; same logic.
  - Blind spot: If timezone becomes a user-editable field in the future, this silent fallback could hide validation errors.
- **Fix B**: Add a catch block in `AuthController.register()` for `IllegalArgumentException` and re-call `registerUser()` with `""` (triggering the blank fallback in the service).
  - Strength: Keeps service throwing; controller explicitly handles the boundary.
  - Tradeoff: More verbose; other callers of `registerUser()` still get the uncaught exception.
  - Confidence: MEDIUM.
  - Blind spot: Other callers not protected unless they also guard.
- **Decision**: FIXED via Fix A

### F2 — ZoneId.of() on stored timezone has no error handling in read paths

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:161,171
- **Detail**: `ZoneId.of(userAccount.getTimezone())` is called bare in `getHistoryEntries()` (line 161) and `getTrendView()` (line 171). Current data is safe (V4 defaults to `Europe/Warsaw`). A corrupt row, a JVM timezone DB update retiring an ID, or a manual DB edit would throw `DateTimeException` and make that user's trends and history pages return 500s indefinitely.
- **Fix**: Wrap both `ZoneId.of()` calls with try/catch that falls back to `ZoneId.of("Europe/Warsaw")` and logs a warning.
- **Decision**: FIXED

### F3 — Dead moodlog.journal.reporting-zone-id property not removed

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/resources/application.properties:30
- **Detail**: `moodlog.journal.reporting-zone-id` still exists in both properties files but is no longer injected anywhere. The plan's "What We're NOT Doing" says the property "can stay as documentation/deployment config" but without a comment it looks active to a future developer.
- **Fix**: Either delete both entries, or add an inline comment `# Deprecated — no longer injected; kept for reference`.
- **Decision**: FIXED (deleted from both properties files)

### F4 — No HTTP-boundary test for invalid timezone

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/test/java/com/amadeuszx/moodlog/user/AuthenticationFlowTests.java
- **Detail**: Tests exist for valid timezone and blank timezone registration, but not for `timezone=JUNK_VALUE`. Without the fix from F1, this path currently 500s. The behavior is untested either way.
- **Fix**: Add `registrationWithInvalidTimezoneFallsBackToDefault()` — POST with `timezone=INVALID` and assert the user is created with `Europe/Warsaw` stored (after resolving F1).
- **Decision**: FIXED (added registrationFallsBackToWarsawWhenInvalidTimezoneSupplied to AuthenticationFlowTests)

### F5 — register.html script lacks null-guard on getElementById

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/resources/templates/register.html:30
- **Detail**: The inline script does `var el = document.getElementById('timezone'); el.value = ...` without guarding `el !== null`. A future template refactor renaming the field would produce a silent TypeError.
- **Fix**: `if (el) { el.value = ...; }` — one-line guard.
- **Decision**: FIXED

### F6 — Raw user-supplied timezone string logged verbatim before validation

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/amadeuszx/moodlog/user/UserAccountService.java:172
- **Detail**: The warn log at line 172 emits `timezone={}` with the raw user-supplied string. A malicious actor submitting a very long string could pollute logs. The 50-char DB constraint doesn't apply pre-validation.
- **Fix**: Cap the logged value: `timezone.substring(0, Math.min(timezone.length(), 64))`.
- **Decision**: FIXED (capped at 64 chars in the log call)

### F7 — assertNotNull via fully-qualified name in UserAccountServiceTests

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/test/java/com/amadeuszx/moodlog/user/UserAccountServiceTests.java:47
- **Detail**: Line 47 calls `org.junit.jupiter.api.Assertions.assertNotNull(exception)` as a fully-qualified call. Every other test file uses static imports. The class is missing the static import.
- **Fix**: Add `import static org.junit.jupiter.api.Assertions.assertNotNull;` and use `assertNotNull(exception)`.
- **Decision**: FIXED

### F8 — @DisplayName and method name slightly diverge in JournalEntryServiceTests

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/test/java/com/amadeuszx/moodlog/journal/JournalEntryServiceTests.java:292
- **Detail**: `@DisplayName("buckets the same UTC instant into different days for Warsaw and New York users")` is accurate but diverges from the method name `bucketsSameUtcInstantIntoDifferentDaysForDifferentTimezones`. Minor; no rule violation.
- **Fix**: Rename method to `bucketsSameUtcInstantIntoDifferentDaysForWarsawAndNewYork` if desired.
- **Decision**: FIXED (renamed to bucketsSameUtcInstantIntoDifferentDaysForWarsawAndNewYork)

### F9 — Default 'Europe/Warsaw' duplicated across three layers

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: V4 SQL migration · UserAccountService.resolveTimezone() · register.html
- **Detail**: `'Europe/Warsaw'` appears in three independent places. For service and JS, a single constant would make a future default change a single edit. The SQL migration literal can stay as sealed history.
- **Fix**: Extract `static final String DEFAULT_TIMEZONE = "Europe/Warsaw"` in `UserAccountService` and reference it from the JS fallback or pass it via Thymeleaf if needed.
- **Decision**: FIXED (extracted DEFAULT_TIMEZONE constant in UserAccountService; SQL migration and JS left as-is)

### F10 — No Lombok on UserAccount entity (pre-existing pattern)

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/main/java/com/amadeuszx/moodlog/user/UserAccount.java
- **Detail**: AGENTS.md prefers Lombok but `UserAccount` has hand-written constructor and getters — consistent with sibling entity `JournalEntry`. Not a regression from this feature.
- **Fix**: Not required for this change. Separate cleanup if the team standardizes.
- **Decision**: FIXED + ACCEPTED-AS-RULE: Entity boilerplate convention (Lombok vs. hand-written)

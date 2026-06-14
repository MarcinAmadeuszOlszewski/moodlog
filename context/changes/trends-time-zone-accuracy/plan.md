# Trends Time Zone Accuracy Implementation Plan

## Overview

Add per-user timezone to `UserAccount`, capture it from the browser at signup, and wire it through `JournalEntryService` to replace the single shared `reportingZoneId`. Also cover the three DST/boundary test gaps identified in research and rename the `windowEndExclusive` footgun.

## Current State Analysis

`JournalEntryService` receives a single app-wide `reportingZoneId` (`Europe/Warsaw` by default) via `@Value` constructor injection. It is used in five functional places across three private methods (`getTrendView`, `toHistoryItem`, `toReportedEntry`). The DB stores UTC `Instant`; zone translation happens entirely in the service; the view layer (`JournalTrendView`) uses `LocalDate`. This is architecturally correct and the boundary computations are DST-safe (`atStartOfDay(ZoneId)`), but every user shares one zone.

`UserAccount` has no timezone field. `UserAccountService.registerUser(String email, String rawPassword)` creates accounts without any locale/zone data. The registration form (`register.html`, `RegistrationForm`, `AuthController`) captures only email and password.

The test class `JournalEntryServiceTests` uses a `@TestConfiguration`-pinned `Clock.fixed("2026-06-01T10:00:00Z")`. The `createUserAccount()` helper directly constructs `new UserAccount(UUID, email, hash, active, createdAt, createdAt)` — it needs updating once `timezone` is added to the constructor.

Three test coverage gaps from research remain: DST spring-forward (March 29), DST fall-back (October 25), and an entry at exactly `windowStartInclusive`.

### Key Discoveries

- `JournalEntryService.java:51,61,70` — `reportingZoneId` field, `@Value` injection, constructor assignment — all three must be removed in Phase 3.
- `JournalEntryService.java:162-169` — `getHistoryEntries()` already calls `resolveUserAccount()` at line 163; the user object is in scope and `userZone` can be derived immediately.
- `JournalEntryService.java:171-227` — `getTrendView()` already calls `resolveUserAccount()` at line 172; same pattern applies.
- `JournalEntryService.java:185` — `windowEndExclusive = now.plusNanos(1)`: the query uses `<`, but the value is `now + 1ns`, so entries at the exact current instant are included. Misleading name; rename to `windowEndInstant`.
- `RegistrationForm.java:6-13` — only `email` and `password`; no `timezone` field yet.
- `register.html:14-29` — form uses `th:object="${registrationForm}"`; hidden field + inline `<script>` can be inserted before the submit button.
- Latest Flyway migration: `V3__nullable_system_mood_score.sql`. Next version: V4.
- Test helper `JournalEntryServiceTests:291-303` — `createUserAccount()` passes 6 args to the `UserAccount` constructor; must be extended to pass timezone.

## Desired End State

Every `UserAccount` row has a non-null IANA timezone string. New users get the zone their browser reports at signup (falling back to `Europe/Warsaw`); existing users are backfilled to `Europe/Warsaw` by V4 migration. `JournalEntryService` derives the zone from `userAccount.getTimezone()` inside `getTrendView()`, `getHistoryEntries()`, and their private helpers — the shared `reportingZoneId` field no longer exists. A user whose timezone is `America/New_York` sees daily and weekly trend boundaries aligned to New York midnight, not Warsaw midnight. All three known DST/boundary coverage gaps are closed by new tests.

Verified by: a per-user timezone integration test (Warsaw user vs. New York user, same UTC instant bucketed differently), plus DST tests for spring-forward, fall-back, and exact-midnight boundary.

### Key Discoveries

- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:51,61,70,162-169,171-195,256-267,407-419`
- `src/main/java/com/amadeuszx/moodlog/user/UserAccount.java:36` — 6-arg constructor, no timezone
- `src/main/java/com/amadeuszx/moodlog/user/UserAccountService.java:45` — `registerUser(String email, String rawPassword)`
- `src/main/java/com/amadeuszx/moodlog/user/register/RegistrationForm.java:6-13`
- `src/main/resources/templates/register.html:14-29`
- `src/main/resources/db/migration/` — V1 through V3; V4 is next
- `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryServiceTests.java:291-303` — `createUserAccount()` helper

## What We're NOT Doing

- Per-user `weekStartDay` preference — `REPORTING_WEEK_START` stays a hardcoded `MONDAY` constant (configurable per deployment via `@Value` if needed later, but not per-user).
- Timezone update on subsequent logins — the stored timezone is set once at signup and is not overwritten on login.
- User-facing timezone picker / settings page — the value is silently detected from the browser and not exposed for manual editing in this change.
- Removing the app-wide `moodlog.journal.reporting-zone-id` property from `application.properties` — it can stay as documentation/deployment config; only the service-level field and injection are removed.

## Implementation Approach

Four phases in dependency order: data layer first (schema + entity + service method signature), then the signup capture surface (form + controller), then service wiring (replace shared field with per-user zone), then test coverage. Each phase is independently verifiable before proceeding.

## Critical Implementation Details

**Timezone injection removal ordering**: `reportingZoneId` is a constructor-injected `@Value` field. When it is removed in Phase 3, the `JournalEntryService` constructor signature changes. Any test context that wires the service via `@SpringBootTest` will need to verify the bean still resolves — the existing `FixedClockConfiguration` test setup does not inject `reportingZoneId` directly, so the removal does not require changes to the nested `@TestConfiguration`. However, the service no longer needs the `${moodlog.journal.reporting-zone-id}` property at all, so any test that sets that property explicitly can drop it.

**JS execution timing**: the hidden timezone field is set by an inline `<script>` tag immediately after the `<input>`. The script must run after the input is rendered but before form submission — inline placement (not `DOMContentLoaded`) ensures this in all browsers.

---

## Phase 1: Schema, Entity, and Registration Service

### Overview

Add the `timezone` column to `user_accounts`, update the `UserAccount` entity and its constructor, and extend `UserAccountService.registerUser()` to accept and persist the timezone.

### Changes Required

#### 1. Flyway migration V4

**File**: `src/main/resources/db/migration/V4__add_timezone_to_user_accounts.sql`

**Intent**: Add a non-null `timezone` column to `user_accounts`, backfilling existing rows with `Europe/Warsaw`.

**Contract**: Single `ALTER TABLE` statement adding `timezone VARCHAR(50) NOT NULL DEFAULT 'Europe/Warsaw'`. No data migration needed beyond the column default.

#### 2. `UserAccount` entity

**File**: `src/main/java/com/amadeuszx/moodlog/user/UserAccount.java`

**Intent**: Add a `timezone` field that persists the IANA zone ID string for each user, and expose it through the constructor and a getter.

**Contract**: Add `@Column(nullable = false, length = 50) private String timezone;` after `updatedAt`. Extend the all-args constructor at line 36 to accept `String timezone` as the seventh parameter. Add `public String getTimezone()` getter.

#### 3. `UserAccountService.registerUser()`

**File**: `src/main/java/com/amadeuszx/moodlog/user/UserAccountService.java`

**Intent**: Accept the user's detected timezone at registration and persist it on the new `UserAccount`. Validate that the supplied value is a recognised IANA zone ID before storing it; fall back to `Europe/Warsaw` if the value is blank.

**Contract**: Extend the method signature at line 45 to `registerUser(String email, String rawPassword, String timezone)`. Before constructing `UserAccount`, resolve the effective zone: use `timezone` if non-blank, otherwise `"Europe/Warsaw"`. Validate via `ZoneId.of(effectiveTimezone)` — let `DateTimeException` propagate as an `IllegalArgumentException` if an explicitly-supplied (non-blank) value is invalid.

### Success Criteria

#### Automated Verification

- `./mvnw test` passes — existing tests compile and pass with the new constructor signature once test helpers are updated (Phase 4 handles test updates; temporarily stub `"Europe/Warsaw"` in test helper if tests are run between phases)
- Flyway migration V4 applies cleanly under both H2 (test) and PostgreSQL (production) profiles
- `UserAccount` entity compiles with the new field and constructor

#### Manual Verification

- No regressions in existing user account behaviour

---

## Phase 2: Signup Form Capture

### Overview

Capture the browser's IANA timezone at signup through a hidden form field populated by inline JavaScript, and thread the value from `RegistrationForm` through `AuthController` into `registerUser()`.

### Changes Required

#### 1. `RegistrationForm`

**File**: `src/main/java/com/amadeuszx/moodlog/user/register/RegistrationForm.java`

**Intent**: Add a `timezone` field to the form binding object so that the hidden input value is bound and available to the controller.

**Contract**: Add `private String timezone;` with no validation annotation (the field is populated by JS, not by the user; the service applies the fallback for blank values). Add `getTimezone()` and `setTimezone(String timezone)`.

#### 2. `register.html`

**File**: `src/main/resources/templates/register.html`

**Intent**: Add a hidden input that is pre-populated with the browser's IANA timezone before the form is submitted. Fall back to `'Europe/Warsaw'` when `Intl.DateTimeFormat` is unavailable.

**Contract**: Insert before the `<button type="submit">` tag:

```html
<input type="hidden" th:field="*{timezone}">
<script>
  (function() {
    var el = document.getElementById('timezone');
    el.value = (typeof Intl !== 'undefined' && Intl.DateTimeFormat)
      ? (Intl.DateTimeFormat().resolvedOptions().timeZone || 'Europe/Warsaw')
      : 'Europe/Warsaw';
  })();
</script>
```

The `th:field="*{timezone}"` binding renders `id="timezone"` and `name="timezone"`, which the inline script targets by ID immediately after the element is parsed.

#### 3. `AuthController.POST /register`

**File**: `src/main/java/com/amadeuszx/moodlog/user/AuthController.java`

**Intent**: Pass the timezone from the bound form to `registerUser()`.

**Contract**: At line 84, change the `userAccountService.registerUser(...)` call to pass `registrationForm.getTimezone()` as the third argument, matching the updated signature from Phase 1.

### Success Criteria

#### Automated Verification

- `./mvnw test` passes
- `MockMvc` test (or existing `AuthController` test if present) verifies that a POST to `/register` with a valid timezone results in a user account being created with that timezone

#### Manual Verification

- Registering a new account in a browser stores the correct timezone (visible via the signup flow; confirm the created `UserAccount` in H2 console or logs)
- If JavaScript is disabled, signup still completes and stores `Europe/Warsaw`
- No regressions in the registration error paths (duplicate email, invalid password)

---

## Phase 3: Service Zone Wiring

### Overview

Replace the shared `reportingZoneId` field in `JournalEntryService` with per-user zone lookups. Both public methods that perform zone-sensitive operations (`getTrendView` and `getHistoryEntries`) already call `resolveUserAccount()` as their first step — derive `userZone` from the resolved account immediately after and thread it into the private helpers.

### Changes Required

#### 1. `JournalEntryService` — field and constructor cleanup

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java`

**Intent**: Remove the class-level `reportingZoneId` field and its `@Value` constructor parameter since the zone is now sourced per-user from the database.

**Contract**: Remove `private final ZoneId reportingZoneId;` (line 51), remove the `@Value("${moodlog.journal.reporting-zone-id:Europe/Warsaw}") String reportingZoneId` constructor parameter (line 61), and remove the `this.reportingZoneId = ZoneId.of(reportingZoneId);` assignment (line 70).

#### 2. `JournalEntryService.getTrendView()`

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java`

**Intent**: Derive the zone from the authenticated user's stored timezone immediately after resolving the account, then use it for all boundary computations and entry bucketing in this method.

**Contract**: After line 172 (`resolveUserAccount`), add `final ZoneId userZone = ZoneId.of(userAccount.getTimezone());`. Replace `reportingZoneId` with `userZone` at lines 174 and 184. Replace `.map(this::toReportedEntry)` with `.map(entry -> toReportedEntry(entry, userZone))`. Rename `windowEndExclusive` at line 185 to `windowEndInstant` and add an inline comment: `// +1ns: makes the < predicate inclusive of entries at the current instant`.

#### 3. `JournalEntryService.getHistoryEntries()`

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java`

**Intent**: Thread the user's zone into the history item mapping so that display dates and times are localised to the requesting user's timezone.

**Contract**: After line 163 (`resolveUserAccount`), add `final ZoneId userZone = ZoneId.of(userAccount.getTimezone());`. Replace `.map(this::toHistoryItem)` at line 168 with `.map(entry -> toHistoryItem(entry, userZone))`.

#### 4. `JournalEntryService.toReportedEntry()`

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java`

**Intent**: Accept the user's zone as an explicit parameter rather than closing over the removed class field.

**Contract**: Extend the method signature to `toReportedEntry(JournalEntryRepository.JournalTrendEntryProjection journalTrendEntry, ZoneId userZone)`. Replace the reference to `reportingZoneId` at line 412 with `userZone`.

#### 5. `JournalEntryService.toHistoryItem()`

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java`

**Intent**: Accept the user's zone as an explicit parameter rather than closing over the removed class field.

**Contract**: Extend the method signature to `toHistoryItem(JournalEntry journalEntry, ZoneId userZone)`. Replace the two references to `reportingZoneId` at lines 258 and 262 with `userZone`.

### Success Criteria

#### Automated Verification

- `./mvnw test` passes — all existing timezone-sensitive service tests continue to pass
- The service bean wires cleanly with no `@Value` injection for `reporting-zone-id`

#### Manual Verification

- History and trend pages render correct dates after the change (Warsaw user sees Warsaw dates, confirmed via manual smoke test)

---

## Phase 4: Tests

### Overview

Update the test infrastructure to work with the new `UserAccount` constructor (timezone arg), add a per-user timezone correctness test confirming different users see different buckets, and add three DST/boundary tests in a new test class that uses `@MockitoBean Clock` for per-test clock control.

### Changes Required

#### 1. `JournalEntryServiceTests.createUserAccount()` helper

**File**: `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryServiceTests.java`

**Intent**: Update the test helper to pass a timezone to the extended `UserAccount` constructor so existing tests compile and run.

**Contract**: At line 293, add `"Europe/Warsaw"` as the seventh argument to `new UserAccount(...)`. No existing test assertions change — the helper is purely a constructor call.

#### 2. Per-user timezone correctness test

**File**: `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryServiceTests.java`

**Intent**: Verify that two users with different timezones bucket the same UTC instant into different `LocalDate` values — confirming the service uses each user's stored zone, not a shared default.

**Contract**: New `@Test` method. Create two accounts: one with `"Europe/Warsaw"`, one with `"America/New_York"`. Insert a single `JournalEntry` with `createdAt = Instant.parse("2026-05-31T22:30:00Z")` owned by each user. With the test clock fixed at `2026-06-01T10:00:00Z`:
- Warsaw user: `2026-05-31T22:30Z` = `2026-06-01T00:30 CEST` → June 1 → appears in `currentWeekSummary` (current week, not completed series)
- New York user: `2026-05-31T22:30Z` = `2026-05-31T18:30 EDT` → May 31 → appears in `completedSevenDayTrend` point for May 31

Assert that `trendView.currentWeekSummary().entriesCount() == 1` for the Warsaw user and `== 0` for the New York user, and vice versa for the seven-day trend.

#### 3. DST and boundary tests — new test class

**File**: `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryServiceDstTests.java`

**Intent**: Cover the three test gaps identified in research: Warsaw spring-forward, Warsaw fall-back, and an entry at exactly `windowStartInclusive`.

**Contract**: `@SpringBootTest` class with `@MockitoBean Clock clock` (no nested `FixedClockConfiguration` — the mock replaces it). In `@BeforeEach`, call `journalEntryRepository.deleteAll()` and `userAccountRepository.deleteAll()`. Each test stubs `given(clock.instant()).willReturn(Instant.parse("..."))` before calling `getTrendView()`.

Three test methods:

- `bucketsEntriesCorrectlyOnWarsawSpringForwardDay`: clock returns `2026-03-29T10:00:00Z`. Create two entries: `2026-03-28T22:59:00Z` (Warsaw `2026-03-28T23:59 CET`, before midnight → March 28 → 7-day completed series) and `2026-03-28T23:00:00Z` (Warsaw `2026-03-29T00:00 CET`, exactly midnight → March 29 → current week). Assert bucketing is correct. This verifies `atStartOfDay(Europe/Warsaw)` on a 23-hour day.

- `bucketsEntriesCorrectlyOnWarsawFallBackDay`: clock returns `2026-10-25T10:00:00Z`. Two entries: `2026-10-24T21:59:00Z` (Warsaw `2026-10-24T23:59 CEST` → October 24) and `2026-10-24T22:00:00Z` (Warsaw `2026-10-25T00:00 CEST` → October 25, the fall-back day). Assert bucketing.

- `includesEntryAtExactWindowStartBoundary`: clock returns `2026-06-01T10:00:00Z`. Compute `windowStartInclusive` = May 25 midnight Warsaw = `Instant.parse("2026-05-24T22:00:00Z")`. Create an entry with `createdAt` equal to that exact Instant. Assert that the entry appears in `completedSevenDayTrend` (confirming `>=` is inclusive).

### Success Criteria

#### Automated Verification

- `./mvnw test` passes — all four new test cases pass alongside the existing suite
- No test compilation errors after `createUserAccount()` helper update

#### Manual Verification

- New tests are readable and use the same assertion style as existing tests in `JournalEntryServiceTests`

---

## Testing Strategy

### Unit Tests

- Per-user timezone correctness (Phase 4, item 2): same UTC instant → different `LocalDate` buckets for Warsaw vs New York users

### Integration Tests

- All tests are `@SpringBootTest` with H2 — existing pattern maintained
- DST tests use `@MockitoBean Clock` for per-test clock positioning without requiring separate Spring context configurations

### Manual Testing Steps

1. Register a new account; check the stored timezone in the database matches the browser's reported zone
2. Travel scenario simulation: register two browser sessions with forced timezones (e.g., via OS or DevTools); confirm trend page shows different daily buckets
3. Disable JavaScript; confirm signup still succeeds and stores `Europe/Warsaw`

## Migration Notes

V4 migration is additive (`ADD COLUMN ... NOT NULL DEFAULT 'Europe/Warsaw'`) and safe to run against a live table. Existing users will silently inherit `Europe/Warsaw`, which matches the current app behaviour — no user-visible change until they register with a different timezone.

## References

- Related research: `context/changes/trends-time-zone-accuracy/research.md`
- Historical context: `context/changes/history-and-mood-trends/research.md` — risk #2 (UTC vs user-local boundaries) and risk #6 (Java-side aggregation)
- Existing timezone tests: `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryServiceTests.java:192-261`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Schema, Entity, and Registration Service

#### Automated

- [x] 1.1 `./mvnw test` passes with updated `UserAccount` constructor (test helper stubbed to `"Europe/Warsaw"`)
- [x] 1.2 Flyway V4 migration applies cleanly under H2 test profile
- [x] 1.3 `UserAccount` entity compiles with new `timezone` field and constructor

#### Manual

- [x] 1.4 No regressions in existing user account behaviour — 6a32800

### Phase 2: Signup Form Capture

#### Automated

- [x] 2.1 `./mvnw test` passes
- [x] 2.2 POST `/register` with valid timezone creates account with that timezone stored

#### Manual

- [x] 2.3 Registering in a browser stores the correct timezone — 36c2c95
- [x] 2.4 Signup with JavaScript disabled completes and stores `Europe/Warsaw` — 36c2c95
- [x] 2.5 No regressions in registration error paths (duplicate email, invalid password) — 36c2c95

### Phase 3: Service Zone Wiring

#### Automated

- [x] 3.1 `./mvnw test` passes — all existing timezone-sensitive service tests continue to pass
- [x] 3.2 `JournalEntryService` bean wires cleanly with no `@Value` injection for `reporting-zone-id`

#### Manual

- [x] 3.3 History and trend pages render correct dates for a Warsaw user — ef21ba5

### Phase 4: Tests

#### Automated

- [x] 4.1 `./mvnw test` passes — all four new test cases pass alongside the existing suite — 51078db
- [x] 4.2 No test compilation errors after `createUserAccount()` helper update — 51078db

#### Manual

- [x] 4.3 New DST and per-user timezone tests are readable and match existing test style — 16b6a4e

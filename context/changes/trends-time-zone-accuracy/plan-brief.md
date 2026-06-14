# Trends Time Zone Accuracy — Plan Brief

> Full plan: `context/changes/trends-time-zone-accuracy/plan.md`
> Research: `context/changes/trends-time-zone-accuracy/research.md`

## What & Why

The mood trends feature currently uses a single shared `reportingZoneId` (`Europe/Warsaw`) for all users. This is correct for the app's current target audience but excludes international users and leaves three known DST/boundary test gaps. This change adds a per-user IANA timezone, captured silently from the browser at signup, and closes the test coverage gaps.

## Starting Point

`UserAccount` has no timezone field. `JournalEntryService` holds `reportingZoneId` as a constructor-injected `@Value` field used in five functional locations. The registration form captures only email and password. DST spring-forward, fall-back, and exact-midnight boundary scenarios have no test coverage.

## Desired End State

Every `UserAccount` row stores a non-null IANA timezone. New users get their browser's detected timezone; existing users are backfilled to `Europe/Warsaw`. `JournalEntryService` derives the zone from `userAccount.getTimezone()` — the shared `reportingZoneId` field no longer exists. A New York user and a Warsaw user creating an entry at the same UTC instant see it in different daily buckets. Three DST/boundary test gaps are closed.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
|---|---|---|---|
| Timezone scope | Per-user (not app-wide only) | User explicitly confirmed this is in scope | Plan |
| Timezone input | Hidden field in signup form, populated by JS | Sets timezone once at account creation with zero user friction | Plan |
| Missing timezone fallback | `Europe/Warsaw` (in service; JS fallback in template) | Matches current behaviour for all existing users; no null-handling needed in service | Plan |
| Migration default | `NOT NULL DEFAULT 'Europe/Warsaw'` | Keeps existing users' behaviour unchanged; avoids nullable-zone complexity in service code | Plan |
| Week start | Stays app-wide `MONDAY` constant | Polish convention; no demand for per-user preference at this stage | Plan |
| windowEndExclusive rename | → `windowEndInstant` + inline comment | Naming footgun flagged by research; 1-line fix with no behaviour change | Research |
| DST coverage | Spring-forward + fall-back + exact midnight (3 new tests) | Closes all three gaps identified in research in one pass | Research |

## Scope

**In scope:**
- V4 Flyway migration: `timezone VARCHAR(50) NOT NULL DEFAULT 'Europe/Warsaw'`
- `UserAccount` entity: `timezone` field + constructor + getter
- `UserAccountService.registerUser()`: extended to accept timezone
- `RegistrationForm` + `register.html`: hidden input + inline JS detection
- `AuthController`: pass timezone through to service
- `JournalEntryService`: remove `reportingZoneId` field; wire per-user zone in `getTrendView`, `getHistoryEntries`, `toReportedEntry`, `toHistoryItem`
- `windowEndExclusive` → `windowEndInstant` rename
- Per-user timezone correctness test (Warsaw vs New York)
- 3 DST/boundary tests in new `JournalEntryServiceDstTests`

**Out of scope:**
- User-facing timezone settings/edit UI
- Updating timezone on subsequent logins
- Per-user `weekStartDay` preference
- Removing `moodlog.journal.reporting-zone-id` from `application.properties`

## Architecture / Approach

`UserAccount` gains a `timezone` String column. At signup, an inline JS snippet reads `Intl.DateTimeFormat().resolvedOptions().timeZone` and writes it to a hidden form field before submission; the controller threads it to the service, which persists it. In `JournalEntryService`, both public zone-sensitive methods (`getTrendView`, `getHistoryEntries`) already resolve the `UserAccount` as their first step — `ZoneId.of(userAccount.getTimezone())` is derived immediately after and passed into the two private helpers (`toReportedEntry`, `toHistoryItem`). The `reportingZoneId` field and its `@Value` injection are removed entirely. No new Spring beans, no new repositories.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. Schema + Entity + Registration Service | `timezone` column + entity + `registerUser()` signature | V4 migration must apply cleanly under H2 and PostgreSQL |
| 2. Signup Form Capture | Hidden input + JS detection + controller wiring | JS disabled → empty field → service must fall back gracefully |
| 3. Service Zone Wiring | `reportingZoneId` removed; per-user zone in all 3 methods | Removing the shared field is a breaking constructor change — test helpers need updating first |
| 4. Tests | Per-user timezone test + 3 DST tests | `@MockitoBean Clock` must stub correctly for `Instant.now(clock)` |

**Prerequisites:** Phase 1 must complete before Phase 3 (service needs `userAccount.getTimezone()` to exist). Test helper update (Phase 4 item 1) should be done alongside Phase 1 to keep the test suite green throughout.  
**Estimated effort:** ~1-2 sessions across 4 phases.

## Open Risks & Assumptions

- `Intl.DateTimeFormat` is available in all target browsers (modern browsers only; IE11 and below lack it — the JS fallback handles this).
- The existing `@SpringBootTest` context does not inject `reporting-zone-id` property explicitly in any test; removing the `@Value` from the service constructor will not break any test context wiring.
- `UserAccountService.registerUser()` has no callers outside of `AuthController` — changing its signature is safe without touching other files.

## Success Criteria (Summary)

- A New York user and a Warsaw user creating a journal entry at `2026-05-31T22:30:00Z` see it in different daily trend buckets
- `./mvnw test` passes green across all four phases
- New accounts created via the signup form store the browser-detected IANA timezone; `Europe/Warsaw` is stored if detection fails

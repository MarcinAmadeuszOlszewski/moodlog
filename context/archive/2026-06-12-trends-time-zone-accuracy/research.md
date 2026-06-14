---
date: 2026-06-12T00:00:00+02:00
researcher: Claude (claude-sonnet-4-6)
git_commit: 1cce35ab3fd839eef1e272c377e90aff2f0763ca
branch: master
repository: MarcinAmadeuszOlszewski/moodlog
topic: "Trends time zone accuracy — how timezone handling affects 7-day, 30-day, and weekly window boundaries"
tags: [research, codebase, trends, timezone, JournalEntryService, JournalTrendView]
status: complete
last_updated: 2026-06-12
last_updated_by: Claude (claude-sonnet-4-6)
---

# Research: Trends Time Zone Accuracy

**Date**: 2026-06-12T00:00:00+02:00
**Researcher**: Claude (claude-sonnet-4-6)
**Git Commit**: `1cce35ab3fd839eef1e272c377e90aff2f0763ca`
**Branch**: `master`
**Repository**: MarcinAmadeuszOlszewski/moodlog

## Research Question

How accurate is the timezone handling in the mood trend feature? Specifically: how are 7-day, 30-day, and weekly window boundaries computed relative to timezones, and where are the gaps?

## Summary

The implementation is **architecturally sound for its intended scope** (single-zone Polish app). Timestamps are stored as UTC `Instant`, all day/week boundary computations use a configurable `reportingZoneId` (default `Europe/Warsaw`), and window-start Instants are derived via DST-safe `atStartOfDay(ZoneId)`. Tests explicitly cover the Warsaw-midnight crossover. The feature is correct for its design assumption: one shared reporting zone for all users.

The main accuracy gaps are: (1) **no per-user timezone** — all users share the single `reportingZoneId`; (2) **no tests for DST transition boundaries** in late March or late October; (3) a minor `windowEndExclusive` naming footgun that is functionally correct but misleading; and (4) an understood design-level overlap between "completed N-day series" and "current week summary" that is intentional.

## Detailed Findings

### 1. How the reporting zone is injected and used

`JournalEntryService` receives `reportingZoneId` as a `ZoneId` via constructor injection from `${moodlog.journal.reporting-zone-id:Europe/Warsaw}`. It is exposed through environment variable `MOODLOG_JOURNAL_REPORTING_ZONE_ID`, so changing the zone for a deployment requires no code change.

`reportingZoneId` is used in five places:

| Location | Purpose |
|---|---|
| `getTrendView()` L174 | Convert `now` to `LocalDate` ("today" in reporting zone) |
| `getTrendView()` L184 | Window-start: `firstReportingDate.atStartOfDay(reportingZoneId).toInstant()` |
| `toReportedEntry()` L412 | Bucket each entry's `createdAt` Instant into a `LocalDate` |
| `toHistoryItem()` L258 | Display date and time for history view |
| `toHistoryItem()` L262 | Display time (truncated to minutes) |

All five are consistent. There is no place where UTC is used for day bucketing instead of `reportingZoneId`.

**Code reference**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:51,70,174,184,258,262,412`

### 2. Window boundary computation — correct and DST-safe

`getTrendView()` derives all boundaries from "today in reporting zone":

```
currentDate          = now.atZone(reportingZoneId).toLocalDate()          // e.g. 2026-06-01 in Warsaw
currentWeekStart     = currentDate.previousOrSame(MONDAY)                 // 2026-06-01
completedSevenDayStart  = currentDate.minusDays(7)                        // 2026-05-25
completedThirtyDayStart = currentDate.minusDays(30)                       // 2026-05-02
firstCompletedWeekStart = currentWeekStart.minusWeeks(8)                  // 2026-04-06
```

The single DB query window starts at:
```java
firstReportingDate.atStartOfDay(reportingZoneId).toInstant()
```

`atStartOfDay(ZoneId)` is the correct DST-safe API — it produces midnight in the named zone even on spring-forward/fall-back days (where midnight is a 23-hour or 25-hour day). Pure calendar arithmetic (`LocalDate.minusDays`) is used before that call, which is correct because it works on dates, not wall-clock intervals.

**Code reference**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:173-195`

### 3. Database query — UTC Instants, Hibernate UTC pin

`JournalEntry.createdAt` is stored as `Instant`. Hibernate is pinned to UTC via `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` in `application.properties:14`. The DB query is:

```java
findTrendEntriesByUserAccountId...CreatedAtGreaterThanEqualAndCreatedAtLessThan(
    userAccountId,
    windowStartInclusive,   // midnight-Warsaw as UTC Instant
    windowEndExclusive      // now + 1ns
)
```

This is correct: the window boundary is a UTC Instant derived from Warsaw midnight, not a literal midnight UTC. An entry at `2026-05-31T22:01:00Z` (Warsaw `2026-06-01T00:01`) is inside the "June 1" window, and an entry at `2026-05-31T21:59:00Z` (Warsaw `2026-05-31T23:59`) is inside "May 31."

**Code reference**:
- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryRepository.java:33-37`
- `src/main/resources/application.properties:14`

### 4. windowEndExclusive naming footgun

```java
final Instant windowEndExclusive = now.plusNanos(1);
```

The variable is named `windowEndExclusive` — suggesting the DB query uses `< windowEndExclusive`, which it does. But the value is `now + 1ns`, so the query effectively matches `createdAt <= now`. This is intentionally inclusive of entries at the exact current instant.

The naming is misleading: a reader sees "exclusive" and expects the value to be a natural boundary (e.g., midnight of tomorrow), not `now + 1ns`. The code is functionally correct but a future maintainer who reads this in isolation may misunderstand the intent.

**Code reference**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:185`

### 5. Series semantics and the current-week overlap

The "completed N-day series" windows cover `[today-N, today-1]` inclusive (past days only). The "current week summary" covers `[currentWeekStart, now]`. When today is mid-week (e.g., Wednesday), days Monday and Tuesday appear in both the 7-day series and the current week summary.

This overlap is **intentional by design**: they answer different questions. The 7-day series is a rolling "7 completed days ending yesterday" view; the current week summary is "what happened so far this week." A plan building on this research should treat the overlap as a documented data behavior, not a bug to fix.

**Code reference**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:283-341`

### 6. No per-user timezone

`UserAccount` has no timezone field. `reportingZoneId` is a single app-wide property. Every user sees day/week boundaries in Warsaw time regardless of their actual location.

For the app's current target audience (Polish users), this is correct. For future international use, this would require: (a) a `timezone` column on `user_accounts`, (b) passing the user's zone into `getTrendView()` and the history methods, and (c) a migration to populate existing rows.

**Code reference**: `src/main/java/com/amadeuszx/moodlog/user/UserAccount.java` (no timezone field)

### 7. Test coverage of timezone boundaries

**What the tests cover:**

The test clock is fixed at `2026-06-01T10:00:00Z`. Warsaw in June is UTC+2 (CEST), so "today" = `2026-06-01`, and Warsaw midnight = `2026-05-31T22:00:00Z`.

`keepsCompletedDailyPeriodsSeparateFromCurrentWeekDataInEuropeWarsaw` explicitly tests the Warsaw midnight crossover:
- Entry at `2026-05-31T21:59:00Z` → Warsaw `2026-05-31T23:59` → date `2026-05-31` → in completed 7-day series
- Entry at `2026-05-31T22:05:00Z` → Warsaw `2026-06-01T00:05` → date `2026-06-01` → in current week summary

`buildsCompletedThirtyDayAndWeeklyTrendsFromEffectiveMoods` tests the 30-day boundary:
- Entry at `2026-05-01T10:00:00Z` → Warsaw `2026-05-01T12:00` → date `2026-05-01` → outside 30-day window (`today-30 = 2026-05-02`)
- Entry at `2026-05-01T22:30:00Z` → Warsaw `2026-05-02T00:30` → date `2026-05-02` → inside window

**What is NOT tested:**

1. **DST spring-forward (last Sunday of March, clocks jump 02:00 → 03:00)**: an entry at `2026-03-29T00:30:00Z` (Warsaw `2026-03-29T01:30 CET`, the hour before the jump) — does the window boundary compute correctly? `atStartOfDay` should handle this, but it is not exercised.

2. **DST fall-back (last Sunday of October, clocks fall 03:00 → 02:00)**: an entry written during the ambiguous hour (`01:30` Warsaw, which occurs twice) — `Instant` is unambiguous, but the conversion to `LocalDate` should still be deterministic.

3. **Entry at exact midnight Warsaw** (`createdAt == windowStartInclusive`): the query uses `>=` so it should be included, but no test covers the exact boundary.

4. **Weekly boundary (Sunday→Monday)**: the test at `2026-05-31T22:05:00Z` covers this, but only for the "current week start" case. No test covers an entry at exactly `currentWeekStart.atStartOfDay(reportingZoneId)`.

**Code reference**:
- `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryServiceTests.java:193-222` — Warsaw midnight crossover
- `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryServiceTests.java:226-261` — 30-day boundary
- `src/test/java/com/amadeuszx/moodlog/journal/JournalTrendsFlowTests.java:57-126` — flow-level rendering

## Code References

- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:51-72` — `reportingZoneId` field and constructor injection
- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:171-227` — `getTrendView()`: window computation, DB query, aggregation
- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:407-419` — `toReportedEntry()`: UTC Instant → Warsaw LocalDate
- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:256-267` — `toHistoryItem()`: UTC Instant → Warsaw date+time for display
- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryRepository.java:33-37` — trend query: `Instant`-bounded, UTC
- `src/main/java/com/amadeuszx/moodlog/journal/trend/JournalTrendView.java` — view model records; uses `LocalDate` throughout (no timezone info leaks into view layer)
- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntry.java:55-61` — `createdAt` / `updatedAt` as `Instant`
- `src/main/resources/application.properties:14,28` — `hibernate.jdbc.time_zone=UTC` and `reporting-zone-id` default
- `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryServiceTests.java:193-261` — timezone-aware service tests
- `src/test/java/com/amadeuszx/moodlog/journal/JournalTrendsFlowTests.java:168-176` — fixed clock at `2026-06-01T10:00:00Z` (Warsaw noon)

## Architecture Insights

- **Clean separation**: DB stores UTC `Instant`; the service layer does all zone translation; the view layer (`JournalTrendView`) uses `LocalDate` (zone-free). No timezone leaks across layer boundaries.
- **Single zone is deliberate**: The app is Polish-targeted (all labels in Polish: "Radość", "Spokój", "Smutek"). `reportingZoneId` as an injectable config + `Clock` injection makes the service fully testable with any fixed point in time and any zone.
- **Java aggregation chosen over DB aggregation**: All grouping-by-date logic runs in Java over a bounded query result set (filtered by owner and time window). This avoids DB-specific date functions and H2/PostgreSQL portability problems with timezone-aware grouping. The trade-off is that all matching entries within the window are loaded into memory before being bucketed — acceptable for personal journal scale.
- **`REPORTING_WEEK_START = DayOfWeek.MONDAY`** is a hardcoded constant. If a user (or locale) prefers Sunday-start weeks, this would need to become configurable.

## Historical Context (from prior changes)

- `context/changes/history-and-mood-trends/research.md` — "Risks and Edge Cases" section, risk #2 ("UTC vs user-local day/week boundaries") explicitly flagged this issue before implementation. The implemented S-03 addressed it by introducing `reportingZoneId`.
- `context/changes/history-and-mood-trends/research.md` — risk #6 ("Database-portable aggregation") recommended Java-side aggregation for H2/PostgreSQL portability. Implemented accordingly.

## Open Questions

1. **Should `REPORTING_WEEK_START` be configurable?** Currently hardcoded to `MONDAY`. Polish convention matches, but this may matter if the app ever targets users who expect Sunday-start weeks.
2. **DST transition test gap**: Should we add a test with a fixed clock during Warsaw's spring-forward week (e.g., `2026-03-30T02:30:00Z`) to exercise `atStartOfDay` on the transition day? This is a correctness confidence gap, not a known bug.
3. **`windowEndExclusive` naming**: Should it be renamed to `windowEndInstant` or `now` to clarify that it is inclusive of the current moment?
4. **Per-user timezone**: Is international use on the roadmap? If yes, `user_accounts` will need a `timezone` (varchar IANA zone id) column and a migration.

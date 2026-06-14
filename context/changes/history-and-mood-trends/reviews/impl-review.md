<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Private history and 7/30-day mood trends

- **Plan**: `context/changes/history-and-mood-trends/plan.md`
- **Scope**: Full plan (Phases 1–4 of 4)
- **Date**: 2026-06-14
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical  4 warnings  4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | WARNING |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Plan Adherence Note

Two positive design drifts — plan intent intact:

1. No global `Europe/Warsaw` property. Zone is per-user on `UserAccount` entity with service-level fallback. More flexible than a global property; plan wording was imprecise.
2. Paging metadata not on `JournalHistoryItem`. Paging state lives as individual model attributes set by `populateHistoryModel()`. Cleaner than stuffing page-level metadata onto a per-row DTO.

## Findings

### F1 — Unplanned package restructuring

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Scope Discipline
- **Location**: N/A (cross-cutting)
- **Detail**: Plan specified all files in flat package `com.amadeuszx.moodlog.*` but implementation reorganized into sub-packages: `journal.*`, `journal.history.*`, `journal.trend.*`, `user.*`. Significant unplanned scope — the structure is clearly better and all 73 tests pass, but the plan never called for it and no addendum was added.
- **Fix A ⭐ Recommended**: Document in plan as addendum — add one sentence noting the package restructure was done as part of this slice.
  - Strength: Keeps plan as source of truth; zero code impact.
  - Tradeoff: Minimal — cosmetic plan edit only.
  - Confidence: HIGH — all tests pass; purely a documentation gap.
  - Blind spot: None significant.
- **Fix B**: No action — treat restructuring as an untracked implementation detail.
  - Strength: Zero effort.
  - Tradeoff: Future reviewers see a major file-path mismatch with no explanation.
  - Confidence: MEDIUM
  - Blind spot: Sets precedent that structural rewrites don't need plan coverage.
- **Decision**: FIXED via Fix A — added Addenda section to plan documenting package restructure and JournalEntryListItem

### F2 — +1ns upper-bound workaround instead of LessThanEqual

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:185`
- **Detail**: `windowEndInstant = now.plusNanos(1)` coerces a strict-less-than repository predicate into an inclusive one. The method name says `LessThan` but the caller inflates the bound by 1ns. A concurrent entry timestamped at `now+1ns` would be unintentionally included.
- **Fix**: Add a `LessThanEqual` repository method (Spring Data keyword) or `@Query` with `<= :windowEnd` and remove the `+1ns` line.
- **Decision**: FIXED — renamed repository method to LessThanEqual, removed +1ns workaround in service, updated repository test to use new method name with adjusted upper bound

### F3 — Dual @Value bindings for historyPageSize and weeklyTrendSpan

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architecture
- **Location**: `src/main/java/com/amadeuszx/moodlog/journal/JournalController.java:33–34`
- **Detail**: `JournalController` binds `historyPageSize` and `weeklyTrendSpan` via `@Value` independently of `JournalEntryService` which also binds the same properties. The controller's `weeklyTrendSpan` is added to the trends model (line 105) as a display hint while the service's copy drives computation. If defaults or property names diverge, the rendered value differs from what was computed — a silent display/data mismatch.
- **Fix A ⭐ Recommended**: Remove controller's copies; expose `weeklyTrendSpan` on `JournalTrendView` (service already has it) and drop the standalone model attribute.
  - Strength: Single source of truth; controller stays thin.
  - Tradeoff: `JournalTrendView` gets a new scalar field; template changes from `${weeklyTrendSpan}` to `${trendView.weeklyTrendSpan}`.
  - Confidence: HIGH — service already has the computed value.
  - Blind spot: Need to confirm `historyPageSize` is not actually used in controller or template beyond the `@Value` binding.
- **Fix B**: Keep both bindings but add a `@PostConstruct` assertion that they match.
  - Strength: Detects config drift at startup; zero UI change.
  - Tradeoff: Defensive code for a situation that shouldn't exist.
  - Confidence: MED
  - Blind spot: Only fires on restart, not during runtime property refresh.
- **Decision**: FIXED via Fix A — added weeklyTrendSpan to JournalTrendView record, service now passes it at construction, controller @Value binding removed, template updated to ${trendView.weeklyTrendSpan}. historyPageSize retained in controller (display-only hint; service independently binds same property).

### F4 — JournalEntry missing Lombok; violates lessons-learned rule

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntry.java:18`
- **Detail**: `JournalEntry` was moved to a new sub-package during this change but not updated to follow the lessons-learned entity rule: `@Getter + @NoArgsConstructor(access = AccessLevel.PROTECTED)`, no `@AllArgsConstructor`. Instead it has 12 hand-written getters and a hand-written protected no-args constructor. The entity was touched (package move) but the alignment was missed.
- **Fix**: Add `@Getter` and `@NoArgsConstructor(access = AccessLevel.PROTECTED)` to `JournalEntry`, remove the 12 hand-written getters and hand-written no-args constructor. Leave the explicit domain constructor.
- **Decision**: FIXED — added @Getter and @NoArgsConstructor(access = AccessLevel.PROTECTED) to JournalEntry, removed 12 hand-written getters and hand-written protected no-args constructor

### F5 — Extra DTO: JournalEntryListItem not in plan

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: `src/main/java/com/amadeuszx/moodlog/journal/history/JournalEntryListItem.java`
- **Detail**: New record `JournalEntryListItem(excerpt, moodLabel, moodScore)` for the recent-10 list on `/journal` is not in the plan. Correctly differentiates from `JournalHistoryItem` (no timestamps needed for the recent list). Benign and correct.
- **Fix**: Document in plan addendum alongside F1.
- **Decision**: SKIPPED — already documented in F1 plan addendum

### F6 — FixedClockConfiguration duplicated across two test classes

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `src/test/java/com/amadeuszx/moodlog/journal/JournalTrendsFlowTests.java:~175` and `JournalEntryServiceTests.java`
- **Detail**: Identical `@TestConfiguration` inner class `FixedClockConfiguration` with `Clock.fixed("2026-06-01T10:00:00Z", ZoneOffset.UTC)` appears in both test classes. Future clock changes require two edits.
- **Fix**: Extract to a shared `FixedClockTestConfiguration` in the test source tree; `@Import` it in both test classes.
- **Decision**: FIXED — extracted FixedClockTestConfiguration as top-level test class; both JournalEntryServiceTests and JournalTrendsFlowTests now use @Import(FixedClockTestConfiguration.class)

### F7 — Out-of-range history page makes two DB round-trips

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/amadeuszx/moodlog/journal/JournalController.java:90–91`
- **Detail**: When `page >= totalPages`, controller issues a second `getHistoryEntries()` call (two user-account lookups + two paged queries). Standard Spring MVC pattern is a redirect to the last valid page URL, which is also bookmarkable.
- **Fix**: Replace second service call with `return "redirect:/journal/history?page=" + (historyPage.getTotalPages() - 1)`.
- **Decision**: FIXED — replaced second service call with redirect to /journal/history?page=<lastPage>; updated test to assert 3xx redirect then follow up with the canonical URL

### F8 — Current-week stat tiles render zeroes when trendView.empty=true

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: `src/main/resources/templates/journal-trends.html:29–46`
- **Detail**: When `trendView.empty` is true, the "add some entries" message renders but the four stat `<article>` tiles also render unconditionally, showing "0", "0", "Brak danych", "Brak danych". Two competing messages coexist. Tests assert the empty-state copy appears but do not assert the stat tiles are hidden.
- **Fix**: Wrap the stats `<div>` in `th:if="${!trendView.currentWeekSummary.empty}"` or `th:if="${!trendView.empty}"`.
- **Decision**: FIXED — added th:if="${!trendView.currentWeekSummary.empty}" to the stats div; also simplified the mood/score expressions since the guard condition makes the ternary redundant

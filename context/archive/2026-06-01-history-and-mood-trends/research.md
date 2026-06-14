---
date: 2026-06-01T21:41:13.5699956+02:00
researcher: GitHub Copilot CLI
git_commit: 2cbd793ab08db8389917e0eff6f135d293714610
branch: master
repository: MarcinAmadeuszOlszewski/moodlog
topic: "Current architecture for history-and-mood-trends"
tags: [research, codebase, journal-history, mood-trends]
status: complete
last_updated: 2026-06-01
last_updated_by: GitHub Copilot CLI
---

# Research: Current architecture for history-and-mood-trends

**Date**: 2026-06-01T21:41:13.5699956+02:00  
**Researcher**: GitHub Copilot CLI  
**Git Commit**: `2cbd793ab08db8389917e0eff6f135d293714610`  
**Branch**: `master`  
**Repository**: `MarcinAmadeuszOlszewski/moodlog`

## Research Question

Analyze the current architecture for the change `history-and-mood-trends`. Determine likely components and risks for private entry history plus 7-day, 30-day, and weekly mood trends.

## Summary

MoodLog is currently a small Spring Boot MVC monolith with Thymeleaf, Spring Security session auth, JPA/Flyway persistence, and integration-heavy `MockMvc` tests. The journal experience is a single authenticated `/journal` page that already supports entry creation plus a newest-first recent-entry list, but it has no dedicated history/trend query layer yet. The cleanest S-03 shape is to extend the existing read side rather than add a new subsystem: keep `/journal` as the private route, add history/trend model data in `JournalEntryService`, back it with new repository date-range/aggregation queries, and expand `journal.html` with server-rendered history + summary sections.

Main risks are not authentication or persistence bootstrapping—they already exist—but read-model design and semantics: full-history pagination, 7/30-day window boundaries, weekly bucketing rules, UTC-vs-user-local day grouping, and whether trend calculations should use system mood only or a future “effective mood” that respects overrides.

## Detailed Findings

### Current live architecture

- The app already uses the full stack needed for S-03: Spring MVC, Thymeleaf, Security, Validation, JPA, Flyway, and tests in one monolith, so this is a read-side extension rather than a platform change ([`pom.xml:44-109`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/pom.xml#L44-L109)).
- `/journal` is the single private route today. `GET /journal` renders the page, `POST /journal` saves a new entry, and both paths already resolve the signed-in user from `Authentication.getName()` ([`src/main/java/com/amadeuszx/moodlog/JournalController.java:31-77`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalController.java#L31-L77)).
- The controller currently builds only one read model: `recentEntries`, mapped to `JournalEntryListItem(excerpt, moodLabel, moodScore)` with Polish mood labels defined inline in the controller ([`src/main/java/com/amadeuszx/moodlog/JournalController.java:69-104`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalController.java#L69-L104), [`src/main/java/com/amadeuszx/moodlog/JournalEntryListItem.java:1-4`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntryListItem.java#L1-L4)).
- The template is still intentionally narrow: one form, one recent-list section, and an empty state for “no saved entries.” There is no history pagination, date display, trend summary, or 7/30-day toggle yet ([`src/main/resources/templates/journal.html:15-40`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/resources/templates/journal.html#L15-L40)).

### Persistence and query seams

- `JournalEntryService` is already the journal orchestration seam. It owns save behavior and one user-scoped read method, `getRecentEntries`, making it the natural place to add methods like `getEntryHistory(...)`, `getTrendWindow(...)`, and `getWeeklySummary(...)` ([`src/main/java/com/amadeuszx/moodlog/JournalEntryService.java:35-73`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntryService.java#L35-L73)).
- The repository only supports newest-first list reads by owner. There are no date-range queries, projections, or grouped aggregations yet, so S-03 will need new repository methods or custom queries ([`src/main/java/com/amadeuszx/moodlog/JournalEntryRepository.java:9-14`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntryRepository.java#L9-L14)).
- The schema already contains most of the raw inputs needed for trends: owner, content, system mood tag/score, future override fields, `classifiedAt`, `createdAt`, and `updatedAt` ([`src/main/java/com/amadeuszx/moodlog/JournalEntry.java:22-59`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntry.java#L22-L59), [`src/main/resources/db/migration/V2__create_journal_entries.sql:1-22`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/resources/db/migration/V2__create_journal_entries.sql#L1-L22)).
- The existing index on `(user_account_id, created_at)` is a good start for owner-scoped history and time-window scans, so S-03 does not obviously need a new index for a first pass ([`src/main/resources/db/migration/V2__create_journal_entries.sql:21-21`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/resources/db/migration/V2__create_journal_entries.sql#L21-L21)).

### Privacy and ownership boundary

- Security is already aligned with the feature: `/journal` and future private routes require authentication, while public routes stay open ([`src/main/java/com/amadeuszx/moodlog/SecurityConfiguration.java:29-71`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/SecurityConfiguration.java#L29-L71)).
- The service resolves the current user from email to `UserAccount`, and the repository scopes reads by `userAccountId`, so private history/trend queries should preserve the same seam instead of re-implementing auth checks in the controller ([`src/main/java/com/amadeuszx/moodlog/JournalEntryService.java:67-73`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntryService.java#L67-L73), [`src/main/java/com/amadeuszx/moodlog/JournalEntryService.java:97-100`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntryService.java#L97-L100)).
- Current integration tests already lock down owner-only visibility and redirect-to-login behavior, which gives S-03 a strong privacy regression net if expanded rather than replaced ([`src/test/java/com/amadeuszx/moodlog/ApplicationTests.java:102-108`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/test/java/com/amadeuszx/moodlog/ApplicationTests.java#L102-L108), [`src/test/java/com/amadeuszx/moodlog/JournalFlowTests.java:177-217`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/test/java/com/amadeuszx/moodlog/JournalFlowTests.java#L177-L217)).

### Product-contract pressure on S-03

- The PRD explicitly expects private history plus 7-day, 30-day, and weekly trend visibility for the signed-in user only ([`context/foundation/prd.md:51-61`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/foundation/prd.md#L51-L61), [`context/foundation/prd.md:79-83`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/foundation/prd.md#L79-L83), [`context/foundation/prd.md:105-115`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/foundation/prd.md#L105-L115)).
- The roadmap deliberately sequences S-03 after persisted classified entries exist and warns against building dashboard breadth before the entry model settles ([`context/foundation/roadmap.md:79-89`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/foundation/roadmap.md#L79-L89)).
- The S-02 plan explicitly deferred trend calculations and weekly summaries, so S-03 is expected to land that breadth now rather than piggyback on the existing “recent 10 entries” view ([`context/changes/first-mood-classified-entry/plan.md:29-35`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/changes/first-mood-classified-entry/plan.md#L29-L35)).

## Likely Components

1. **Controller/model composition**
   - Extend `JournalController` model population with full history and trend model attributes, or split read-model assembly into helper methods while keeping `/journal` as the main private page ([`src/main/java/com/amadeuszx/moodlog/JournalController.java:31-77`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalController.java#L31-L77)).
2. **Read-side application service**
   - Add history and trend methods to `JournalEntryService`; this is the cleanest existing seam because ownership resolution already lives there ([`src/main/java/com/amadeuszx/moodlog/JournalEntryService.java:67-100`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntryService.java#L67-L100)).
3. **Repository projections or custom queries**
   - Introduce date-window reads and probably projection DTOs for grouped trend data instead of forcing the controller to aggregate raw entities ([`src/main/java/com/amadeuszx/moodlog/JournalEntryRepository.java:9-14`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntryRepository.java#L9-L14)).
4. **New DTOs/view models**
   - `JournalEntryListItem` is too narrow for created-at timestamps, pagination metadata, and trend buckets, so S-03 likely needs dedicated history and trend DTOs ([`src/main/java/com/amadeuszx/moodlog/JournalEntryListItem.java:1-4`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntryListItem.java#L1-L4)).
5. **Template expansion**
   - `journal.html` will need at least one history section and one trend section, still rendered server-side to match the current MVC architecture ([`src/main/resources/templates/journal.html:15-40`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/resources/templates/journal.html#L15-L40)).
6. **Test expansion**
   - Current tests cover write flow, owner scoping, and newest-first recent lists, but not trend math, window boundaries, or empty/sparse trend states ([`src/test/java/com/amadeuszx/moodlog/JournalEntryServiceTests.java:46-149`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/test/java/com/amadeuszx/moodlog/JournalEntryServiceTests.java#L46-L149), [`src/test/java/com/amadeuszx/moodlog/JournalEntryRepositoryTests.java:32-120`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/test/java/com/amadeuszx/moodlog/JournalEntryRepositoryTests.java#L32-L120), [`src/test/java/com/amadeuszx/moodlog/JournalFlowTests.java:63-217`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/test/java/com/amadeuszx/moodlog/JournalFlowTests.java#L63-L217)).

## Implementation Seams

- **Stay on `/journal` first.** Current auth redirects, saved-request behavior, and tests already target `/journal`, so augmenting that page is lower-risk than introducing a second private route immediately ([`src/main/java/com/amadeuszx/moodlog/SecurityConfiguration.java:55-80`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/SecurityConfiguration.java#L55-L80), [`src/test/java/com/amadeuszx/moodlog/AuthenticationFlowTests.java:189-252`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/test/java/com/amadeuszx/moodlog/AuthenticationFlowTests.java#L189-L252)).
- **Add a read-only trend service layer before touching SQL-heavy controller logic.** The service already owns user lookup and can hide “which timestamp / which mood / which window” decisions from MVC code ([`src/main/java/com/amadeuszx/moodlog/JournalEntryService.java:67-100`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntryService.java#L67-L100)).
- **Move mood-label presentation out of the controller if trends need repeated labels.** `polishMoodLabel(...)` is private and controller-local today, which is fine for one list but brittle once trend summaries and weekly dashboard cards need the same mapping ([`src/main/java/com/amadeuszx/moodlog/JournalController.java:94-103`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalController.java#L94-L103)).
- **Prefer DTO/projection outputs over exposing entities to the view.** `spring.jpa.open-in-view=false` means trend/history pages should not rely on lazy entity traversal in Thymeleaf ([`src/main/resources/application.properties:12-14`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/resources/application.properties#L12-L14)).

## Risks and Edge Cases

1. **Timestamp semantics**
   - Current ordering uses `createdAt`, but trend math could plausibly use `createdAt`, `classifiedAt`, or later “effective mood updated at” if overrides exist. Pick one before building queries ([`src/main/java/com/amadeuszx/moodlog/JournalEntry.java:52-59`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntry.java#L52-L59), [`src/main/java/com/amadeuszx/moodlog/JournalEntryService.java:39-52`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntryService.java#L39-L52)).
2. **UTC vs user-local day/week boundaries**
   - Hibernate is pinned to UTC, so “today,” “last 7 days,” and “weekly summary” can feel wrong near midnight unless the product explicitly accepts UTC semantics ([`src/main/resources/application.properties:13-14`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/resources/application.properties#L13-L14)).
3. **History breadth vs current recent-list limit**
   - The current read path caps the list at 10 entries through configuration, which is fine for recents but not for a true history feature. Decide whether S-03 means full history, paginated history, or “recent history + trends” ([`src/main/resources/application.properties:25-26`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/resources/application.properties#L25-L26), [`src/main/java/com/amadeuszx/moodlog/JournalEntryService.java:67-73`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntryService.java#L67-L73)).
4. **Override precedence**
   - The schema already reserves `overrideMoodTag` and `overrideMoodScore`, but current UI/service ignore them. Trends built only on `systemMood*` may need rework once S-04 lands ([`src/main/java/com/amadeuszx/moodlog/JournalEntry.java:39-44`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntry.java#L39-L44)).
5. **Product-contract mismatch around failed classification**
   - The PRD says failed classification should still save the entry with unknown mood, but the implemented S-02 plan chose blocking save instead. S-03 trend counts are simpler today because every saved row has a non-null system mood, but that simplicity may be temporary if product alignment changes later ([`context/foundation/prd.md:46-47`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/foundation/prd.md#L46-L47), [`context/foundation/prd.md:113-115`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/foundation/prd.md#L113-L115), [`context/changes/first-mood-classified-entry/plan.md:13-13`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/changes/first-mood-classified-entry/plan.md#L13-L13), [`context/changes/first-mood-classified-entry/plan.md:43-45`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/changes/first-mood-classified-entry/plan.md#L43-L45), [`context/changes/first-mood-classified-entry/research.md:82-84`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/changes/first-mood-classified-entry/research.md#L82-L84)).
6. **Database-portable aggregation**
   - Runtime defaults currently use H2 in PostgreSQL mode, while the build also includes PostgreSQL/Flyway support. Weekly/grouped queries that lean on DB-specific date functions may become awkward across environments ([`src/main/resources/application.properties:8-14`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/resources/application.properties#L8-L14), [`src/test/resources/application.properties:1-16`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/test/resources/application.properties#L1-L16)).

## Recommended Complexity

**Recommended complexity: Medium**

Why:

- The app already has the hard foundations: auth, private route, persistence, owner scoping, and journal tests.
- S-03 is mostly a read-model and UI expansion, not a new subsystem.
- The main complexity is semantic: date windows, weekly grouping, and future override compatibility.

This becomes **Medium-High** only if the change also tries to realign S-02 with the PRD’s “save unknown mood on failure” behavior or introduces heavy database-side weekly aggregation/charting in one step.

## Code References

- [`src/main/java/com/amadeuszx/moodlog/JournalController.java:31-104`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalController.java#L31-L104) - Current `/journal` read/write flow and inline list-item mapping.
- [`src/main/java/com/amadeuszx/moodlog/JournalEntryService.java:35-100`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntryService.java#L35-L100) - Save orchestration, current recent-entry query, and user-account resolution seam.
- [`src/main/java/com/amadeuszx/moodlog/JournalEntryRepository.java:9-14`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntryRepository.java#L9-L14) - Existing newest-first owner-scoped queries.
- [`src/main/java/com/amadeuszx/moodlog/JournalEntry.java:22-59`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/java/com/amadeuszx/moodlog/JournalEntry.java#L22-L59) - Persisted mood/timestamp/override fields.
- [`src/main/resources/templates/journal.html:15-40`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/main/resources/templates/journal.html#L15-L40) - Current page shape with no history/trend sections.
- [`src/test/java/com/amadeuszx/moodlog/JournalFlowTests.java:177-217`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/src/test/java/com/amadeuszx/moodlog/JournalFlowTests.java#L177-L217) - UI-level owner scoping and newest-first assumptions.
- [`context/foundation/prd.md:51-61`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/foundation/prd.md#L51-L61) - US-01 acceptance criteria linking save, history, and trend updates.
- [`context/foundation/prd.md:105-115`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/foundation/prd.md#L105-L115) - Trend requirement and owner-only visibility constraints.

## Architecture Insights

- The repo is optimized for small vertical Spring slices with integration-first verification, so S-03 should likely follow the same pattern: repository query additions -> service aggregation -> controller model composition -> Thymeleaf rendering -> `MockMvc` regression coverage.
- The current design already separates write behavior from read behavior enough to add trend queries without reworking auth or AI integration.
- The biggest architectural choice is whether trend aggregation should happen mostly in Java over owner/date-scoped entry sets or mostly in custom SQL. Given current scale and dual H2/PostgreSQL setup, Java aggregation over bounded windows is the safer first implementation.

## Historical Context (from prior changes)

- S-02 intentionally stopped at save + classify + recent list and deferred trend work to S-03 ([`context/changes/first-mood-classified-entry/plan.md:29-35`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/changes/first-mood-classified-entry/plan.md#L29-L35)).
- S-02 also chose blocking save on classifier failure, even though earlier research argued for save-first with unknown mood. That unresolved tension directly affects future history/trend semantics ([`context/changes/first-mood-classified-entry/plan.md:13-13`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/changes/first-mood-classified-entry/plan.md#L13-L13), [`context/changes/first-mood-classified-entry/research.md:127-129`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/changes/first-mood-classified-entry/research.md#L127-L129)).
- The roadmap already frames S-03’s main failure mode as “dashboard breadth before the entry model settles,” which still looks accurate in the live code ([`context/foundation/roadmap.md:81-89`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/foundation/roadmap.md#L81-L89)).

## Related Research

- [`context/changes/first-mood-classified-entry/research.md`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/2cbd793ab08db8389917e0eff6f135d293714610/context/changes/first-mood-classified-entry/research.md)

## Open Questions

1. Should “7-day” and “30-day” mean rolling windows ending now, or day-bucketed windows ending today?
2. Should weekly summary mean calendar week, ISO week, or “last 7 days grouped by day”?
3. Should S-03 compute trends from `systemMood*` only, or introduce an “effective mood” rule now to prepare for S-04 overrides?
4. Does FR-003 require full paginated history now, or is “recent history + trends” still acceptable for this slice?

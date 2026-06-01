# Private history and mood trends Implementation Plan

## Overview

Extend the existing private journal read side so the signed-in user can still write on `/journal`, browse paginated private history on `/journal/history`, and review chart-backed mood trends on `/journal/trends`. The new trend view uses effective mood data (override if present, otherwise system mood), Europe/Warsaw calendar boundaries, completed 7-day and 30-day periods, a current-week summary, and a multi-week weekly chart without introducing a new frontend stack or analytics subsystem.

## Current State Analysis

The app already has the hard foundations for this slice: `/journal` is an authenticated route with a working GET + POST flow, journal entries persist with owner scoping, and the current page renders the latest entries newest-first with Polish mood labels (`src/main/java/com/amadeuszx/moodlog/JournalController.java:31-104`, `src/main/java/com/amadeuszx/moodlog/JournalEntryService.java:35-73`, `src/main/java/com/amadeuszx/moodlog/JournalEntryRepository.java:9-14`, `src/main/resources/templates/journal.html:15-40`). Auth, redirect-to-login, and saved-request behavior are already in place at the Spring Security layer, so S-03 does not need a new access-control model (`src/main/java/com/amadeuszx/moodlog/SecurityConfiguration.java:37-70`, `src/test/java/com/amadeuszx/moodlog/AuthenticationFlowTests.java:189-252`).

The current read side is intentionally narrow. `JournalEntry` already stores system mood, future override fields, and the timestamps needed for history/trend calculations, but there is no paginated history route, no trend DTO layer, no date-bucket aggregation, and no chart UI yet (`src/main/java/com/amadeuszx/moodlog/JournalEntry.java:22-59`, `src/main/resources/db/migration/V2__create_journal_entries.sql:1-22`). The repository is still limited to newest-first owner-scoped list reads, while `spring.jpa.open-in-view=false` means the new pages should be fed by explicit DTOs rather than lazy entity traversal (`src/main/resources/application.properties:12-26`, `src/test/resources/application.properties:1-16`).

S-02 deliberately stopped at save + classify + recent list and deferred trend breadth to this slice, which makes S-03 a read-model expansion rather than a write-flow redesign (`context/changes/first-mood-classified-entry/plan.md:29-35`). The remaining complexity is semantic: date and week boundaries, sparse-data behavior, future override precedence, and keeping the new history/trend pages owner-only without overbuilding drill-down or dashboard scope (`context/changes/history-and-mood-trends/research.md:82-95`).

## Desired End State

An authenticated user can:

- keep using `/journal` as the write surface and latest-10 recap
- open `/journal/history` to review their older entries in a paginated, owner-only list
- open `/journal/trends` to see summary cards plus charted mood trends for completed 7-day and 30-day periods, an in-progress current-week summary, and a multi-week weekly chart

History and trends are computed from an internal effective-mood rule that prefers override fields when present and otherwise uses the current system mood fields. All date and week bucketing follows Europe/Warsaw calendar semantics, 7-day and 30-day charts exclude the current partial day, missing days remain gaps rather than invented zeroes, and the first implementation is optimized for hundreds of entries per user rather than large-scale analytics.

### Key Discoveries:

- `JournalEntryService` is already the ownership-aware orchestration seam and is the safest place to centralize history/trend reads (`src/main/java/com/amadeuszx/moodlog/JournalEntryService.java:67-100`).
- The persisted journal schema already contains the raw inputs needed for history and trends, including future override fields and all relevant timestamps (`src/main/java/com/amadeuszx/moodlog/JournalEntry.java:32-59`).
- `spring.jpa.open-in-view=false` makes explicit view DTOs a requirement for the new pages, not an optional cleanup (`src/main/resources/application.properties:12-14`).
- Runtime and test environments both rely on H2/PostgreSQL-compatible behavior, so bounded Java-side aggregation is safer than DB-specific week/date SQL for the first pass (`src/main/resources/application.properties:8-14`, `src/test/resources/application.properties:1-16`).
- S-02 intentionally left history/trend breadth for this change, but it also kept the write flow on `/journal`, so S-03 must extend rather than replace that flow (`context/changes/first-mood-classified-entry/plan.md:199-242`).

## What We're NOT Doing

- Adding an entry-detail route or clickable drill-down from chart points
- Implementing edit, delete, or mood-override UI from S-04
- Reworking the S-02 decision to block persistence on classification failure
- Adding per-user timezone settings or multi-timezone support
- Optimizing for tens of thousands of entries or introducing a reporting-specific data store
- Introducing a SPA framework, API-first dashboard, or frontend build pipeline

## Implementation Approach

Keep the journal feature as a single vertical Spring Boot slice centered on the existing journal controller/service/repository seam. `/journal` remains the authenticated write surface and latest-10 recap, while new GET routes expose owner-scoped history and trends from the same service layer. Repository changes stay narrow: paginated owner history and bounded owner/date reads. Trend math happens in Java over bounded entry sets so the app can preserve the current H2/PostgreSQL portability and avoid brittle DB-specific week functions.

The UI stays server-rendered with Thymeleaf. History gets its own template with standard pagination and no drill-down route. Trends get a dedicated template that renders summary cards plus Chart.js visualizations from server-prepared labels and values; the browser should never fetch or aggregate journal data on its own. Shared presentation concerns such as Poland-facing mood labels and effective mood resolution move out of ad-hoc controller code into reusable read-side helpers or DTO assembly.

## Critical Implementation Details

### Timing & lifecycle

Trend and history bucketing must convert persisted UTC timestamps into Europe/Warsaw before assigning entries to days or weeks. The 7-day and 30-day charts use the last completed periods only; the current-week summary is week-to-date and should stay visually distinct from those completed-period datasets.

### State sequencing

Introduce the effective-mood rule in the read layer before building any history rows or trend datasets, then feed every page from that contract. Because the app runs with `open-in-view=false` and dual H2/PostgreSQL environments, aggregate bounded owner-scoped entry sets in Java instead of pushing week/date logic into DB-specific SQL on the first pass.

## Phase 1: Shared read-side foundation

### Overview

Add the shared contracts, queries, and configuration needed to support private history and trend reporting without changing the existing write flow.

### Changes Required:

#### 1. Reporting configuration

**File**: `src/main/resources/application.properties`

**Intent**: Add property-driven settings for journal history and trend calculations so the read-side rules stay explicit and testable.

**Contract**: Define configuration for the reporting timezone (`Europe/Warsaw`), history page size (`20`), and weekly trend span (`8` completed weeks) while preserving the current latest-10 `/journal` recap and existing journal write settings.

**File**: `src/test/resources/application.properties`

**Intent**: Keep test boot behavior aligned with the new read-side settings.

**Contract**: Mirror the new journal reporting properties in the test profile so repository, service, and MVC tests execute with the same date-boundary and paging assumptions as the main app.

#### 2. Repository read extensions

**File**: `src/main/java/com/amadeuszx/moodlog/JournalEntryRepository.java`

**Intent**: Extend the current owner-scoped repository with the minimum reads needed for full history and bounded trend aggregation.

**Contract**: Keep the existing latest-10 query for `/journal`, add paginated newest-first history reads for one owner, and add bounded owner/date queries suitable for Java-side aggregation without exposing cross-user access paths.

#### 3. History and trend read models

**File**: `src/main/java/com/amadeuszx/moodlog/JournalHistoryItem.java`

**Intent**: Introduce a dedicated history DTO instead of stretching the current recent-entry record across multiple pages.

**Contract**: Represent one history row with Europe/Warsaw display date/time, excerpt, effective mood label, effective mood score, and any paging-related metadata the template needs.

**File**: `src/main/java/com/amadeuszx/moodlog/JournalTrendView.java`

**Intent**: Define the server-side contract for the trends page in one typed view model.

**Contract**: Carry current-week summary fields, completed 7-day and 30-day datasets, eight completed weekly buckets, empty/sparse-state flags, and chart-ready labels/values prepared entirely on the server.

#### 4. Shared journal read orchestration

**File**: `src/main/java/com/amadeuszx/moodlog/JournalEntryService.java`

**Intent**: Centralize history and trend reads in the same service that already resolves the authenticated owner.

**Contract**: Add methods for paginated history and trend-view assembly, implement an internal effective-mood rule that prefers override values when present, and keep all owner resolution inside the service rather than scattering it across controllers.

#### 5. Read-side verification

**File**: `src/test/java/com/amadeuszx/moodlog/JournalEntryRepositoryTests.java`

**Intent**: Prove the new history queries stay newest-first and owner-only before MVC pages depend on them.

**Contract**: Cover paginated history ordering, owner scoping, and the bounded date-read shape used by trend aggregation.

**File**: `src/test/java/com/amadeuszx/moodlog/JournalEntryServiceTests.java`

**Intent**: Lock down the new read-side semantics where they are easiest to reason about.

**Contract**: Cover effective-mood precedence, Europe/Warsaw bucketing, completed 7-day and 30-day period selection, current-week summary calculation, weekly bucket generation, and sparse-data handling.

### Success Criteria:

#### Automated Verification:

- Repository and service tests prove paginated owner history plus bounded date-window reads: `.\mvnw.cmd -q "-Dtest=JournalEntryRepositoryTests,JournalEntryServiceTests" test`
- Service tests prove effective-mood precedence and completed-period bucketing in Europe/Warsaw: `.\mvnw.cmd -q "-Dtest=JournalEntryServiceTests" test`

#### Manual Verification:

- The journal feature still boots with the existing `/journal` write flow unchanged while the new read-side contracts remain additive
- The agreed reporting rules are explicit and reviewable: Europe/Warsaw boundaries, completed 7-day and 30-day periods, eight completed weekly buckets, and hundreds-scale bounded reads

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 2: Private history browsing

### Overview

Add a dedicated owner-only history page while keeping `/journal` focused on writing and the latest 10 entries.

### Changes Required:

#### 1. Journal routes and navigation

**File**: `src/main/java/com/amadeuszx/moodlog/JournalController.java`

**Intent**: Serve the three journal surfaces without breaking the existing write and redirect flow.

**Contract**: Keep `GET /journal` and `POST /journal` behavior intact, add `GET /journal/history` with page navigation, and expose clear navigation links between `/journal`, `/journal/history`, and `/journal/trends`.

#### 2. Write page recap contract

**File**: `src/main/resources/templates/journal.html`

**Intent**: Preserve `/journal` as the entry form and latest-10 recap instead of turning it into the full archive.

**Contract**: Continue rendering only the latest 10 entries, retain the existing submit flow, and add explicit links into the history and trends pages without implying a detail route.

#### 3. History page template

**File**: `src/main/resources/templates/journal-history.html`

**Intent**: Create the first full private browsing surface for saved entries.

**Contract**: Render a paginated newest-first list with 20 entries per page, Europe/Warsaw display dates/times, excerpt-based rows, effective mood label + score, empty-state copy, and previous/next navigation; do not add detail links or editing controls.

#### 4. MVC history coverage

**File**: `src/test/java/com/amadeuszx/moodlog/JournalFlowTests.java`

**Intent**: Extend the existing journal integration suite with history-specific browsing behavior.

**Contract**: Cover latest-10 behavior on `/journal`, authenticated rendering of `/journal/history`, newest-first paging, owner-only visibility, and empty-state behavior when the user has no saved history.

**File**: `src/test/java/com/amadeuszx/moodlog/ApplicationTests.java`

**Intent**: Preserve the repo's application-level smoke coverage as private routes expand.

**Contract**: Keep public route coverage intact while asserting that unauthenticated `/journal/history` requests still redirect to login.

### Success Criteria:

#### Automated Verification:

- MVC tests cover `/journal` as the latest-10 recap and `/journal/history` as the paginated private archive: `.\mvnw.cmd -q "-Dtest=JournalFlowTests,ApplicationTests" test`
- History tests prove paging, newest-first ordering, and owner-only visibility in the UI layer: `.\mvnw.cmd -q "-Dtest=JournalFlowTests" test`

#### Manual Verification:

- A logged-in user can move from `/journal` to `/journal/history` and browse older entries without seeing another user's data
- `/journal` still shows only the latest 10 entries even when the user has a longer history available on the archive page

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 3: Mood trends analytics

### Overview

Add the dedicated trends page with summary cards and charted datasets built from the shared read-side rules chosen during planning.

### Changes Required:

#### 1. Trend aggregation rules

**File**: `src/main/java/com/amadeuszx/moodlog/JournalEntryService.java`

**Intent**: Assemble all trend-facing data in one owner-scoped application service rather than splitting analytics logic across the controller and template.

**Contract**: Produce completed 7-day and 30-day daily datasets, a current-week summary (entries count, dominant effective mood, average effective score, and days-with-entries), and eight completed weekly buckets, all using Europe/Warsaw boundaries, effective mood, and gap-aware sparse-data rules.

#### 2. Trend route and page model

**File**: `src/main/java/com/amadeuszx/moodlog/JournalController.java`

**Intent**: Expose the dedicated analytics page alongside the existing journal routes.

**Contract**: Add `GET /journal/trends`, populate the model exclusively from the service-level trend view, and keep the page owner-only without adding client-side fetch endpoints or cross-user parameters.

#### 3. Trends template and chart initialization

**File**: `src/main/resources/templates/journal-trends.html`

**Intent**: Render the agreed analytics surface with summary cards, gap-aware charts, and clear sparse/empty states.

**Contract**: Show the current-week summary separately from the completed-period charts, render 7-day and 30-day datasets as daily charts, render the weekly chart from the last eight completed weeks, and expose only server-prepared labels/values to the browser.

**File**: `src/main/resources/static/js/journal-trends.js`

**Intent**: Keep the Chart.js setup small, predictable, and independent from the journaling domain logic.

**Contract**: Initialize charts from server-rendered data attributes or JSON blobs only; no client-side aggregation, filtering, or data fetching belongs in this file.

#### 4. Trend-specific verification

**File**: `src/test/java/com/amadeuszx/moodlog/JournalEntryServiceTests.java`

**Intent**: Protect the analytics math from regression as the page evolves.

**Contract**: Extend service coverage for gap generation, completed-period exclusion of today's partial data, current-week summary metrics, eight-week grouping, and effective-mood precedence.

**File**: `src/test/java/com/amadeuszx/moodlog/JournalTrendsFlowTests.java`

**Intent**: Add an integration-first safety net for the trends page without overloading the existing journal write-flow tests.

**Contract**: Cover authenticated rendering of `/journal/trends`, empty/sparse-state copy, chart-ready model data, and owner-only analytics visibility.

### Success Criteria:

#### Automated Verification:

- Service tests prove trend bucketing, current-week summary math, and gap-aware completed-period datasets: `.\mvnw.cmd -q "-Dtest=JournalEntryServiceTests" test`
- MVC tests render `/journal/trends` with the expected summary-card and chart-data shape for empty and populated users: `.\mvnw.cmd -q "-Dtest=JournalTrendsFlowTests" test`

#### Manual Verification:

- `/journal/trends` shows stable 7-day and 30-day charts that do not include today's partial data
- The current-week summary and the weekly chart both feel consistent with Europe/Warsaw calendar expectations and do not invent data for missing days

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 4: Navigation and regression hardening

### Overview

Finish the slice by locking down cross-page navigation, auth behavior, and the full regression surface for the expanded journal area.

### Changes Required:

#### 1. Auth and saved-request coverage

**File**: `src/test/java/com/amadeuszx/moodlog/AuthenticationFlowTests.java`

**Intent**: Ensure the new private routes inherit the same login and redirect guarantees as the original `/journal` route.

**Contract**: Cover unauthenticated requests to `/journal/history` and `/journal/trends`, then prove that login returns the user to the originally requested private page instead of collapsing everything back to `/journal`.

#### 2. Cross-page navigation polish

**File**: `src/main/resources/templates/journal.html`

**Intent**: Make the three journal surfaces feel like one coherent private area.

**Contract**: Keep navigation labels, empty-state tone, and logout/home affordances consistent with the new history and trends pages while avoiding links to nonexistent detail or edit flows.

**File**: `src/main/resources/templates/journal-history.html`

**Intent**: Align the archive page with the rest of the journal UX.

**Contract**: Use the same navigation vocabulary and owner-only framing as `/journal` and `/journal/trends`.

**File**: `src/main/resources/templates/journal-trends.html`

**Intent**: Keep analytics navigation and empty-state messaging coherent with the write and history pages.

**Contract**: Present trend summaries as an extension of the private journal, not as a separate dashboard product or public report.

#### 3. Application-level regression

**File**: `src/test/java/com/amadeuszx/moodlog/ApplicationTests.java`

**Intent**: Preserve the repo's public-route confidence while the private journal area grows.

**Contract**: Keep `/` and `/v1/random` behavior intact, and extend application-level assertions so all journal surfaces remain authenticated-only.

### Success Criteria:

#### Automated Verification:

- Auth and application regression cover saved-request redirects plus public-route stability across all journal surfaces: `.\mvnw.cmd -q "-Dtest=ApplicationTests,AuthenticationFlowTests,JournalFlowTests,JournalTrendsFlowTests" test`
- Full regression passes with history and trends coverage included: `.\mvnw.cmd test`

#### Manual Verification:

- A user can sign in, write on `/journal`, browse `/journal/history`, and inspect `/journal/trends` without dead-end navigation
- No page exposes another user's data or suggests unsupported drill-down, edit, or correction behavior

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

## Testing Strategy

### Unit Tests:

- Effective-mood resolution prefers override fields over system fields when both exist
- Europe/Warsaw day and week bucketing behave correctly around midnight and week boundaries
- Completed 7-day and 30-day datasets exclude today's partial data while current-week summary stays week-to-date
- Sparse-data handling leaves gaps instead of synthesizing zero-score or carry-forward values

### Integration Tests:

- `/journal` still shows only the latest 10 entries after more than 10 private entries exist
- `/journal/history` paginates newest-first and never leaks other users' rows
- `/journal/trends` renders correctly for both empty and populated users
- Unauthenticated requests to `/journal/history` and `/journal/trends` return through login to the original route
- Existing public routes and journal write flow keep working after the new pages land

### Manual Testing Steps:

1. Sign in as one user, create more than 10 entries across multiple dates, and verify `/journal` remains a latest-10 recap.
2. Open `/journal/history`, page through the archive, and confirm entries stay newest-first with no detail links.
3. Open `/journal/trends` and verify completed 7-day and 30-day charts show gaps for missing days rather than invented values.
4. Confirm the current-week summary and weekly chart reflect Europe/Warsaw expectations near day/week boundaries.
5. Log out, request `/journal/history` or `/journal/trends`, and verify login returns to the originally requested page.

## Performance Considerations

The first implementation only needs to handle hundreds of entries per user comfortably, so history should remain paginated and trend aggregation should stay bounded. `/journal` continues to fetch only the latest 10 entries, `/journal/history` loads 20 rows per page, and `/journal/trends` should aggregate only the completed 7-day, completed 30-day, current-week, and eight-week windows needed for the page. Avoid DB-specific weekly aggregation or full-history scans in the request path unless targeted validation shows the bounded Java approach is insufficient.

## Migration Notes

No schema migration is expected for S-03 because the existing `journal_entries` table already contains ownership, timestamp, system mood, and override mood fields. If later slices need persisted reporting preferences, precomputed aggregates, or a different classification-failure model, that should land as a follow-up change rather than be folded into this slice.

## References

- Related research: `context/changes/history-and-mood-trends/research.md`
- Prior slice handoff: `context/changes/first-mood-classified-entry/plan.md:29-35`
- Current `/journal` flow: `src/main/java/com/amadeuszx/moodlog/JournalController.java:31-104`
- Current journal service seam: `src/main/java/com/amadeuszx/moodlog/JournalEntryService.java:35-100`
- Existing owner-scoped reads: `src/main/java/com/amadeuszx/moodlog/JournalEntryRepository.java:9-14`
- Existing journal schema: `src/main/java/com/amadeuszx/moodlog/JournalEntry.java:22-59`
- Current journal template: `src/main/resources/templates/journal.html:15-40`
- Auth boundary and saved-request behavior: `src/main/java/com/amadeuszx/moodlog/SecurityConfiguration.java:37-80`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Shared read-side foundation

#### Automated

- [x] 1.1 Repository and service tests prove paginated owner history plus bounded date-window reads — 000bd00
- [x] 1.2 Service tests prove effective-mood precedence and completed-period bucketing in Europe/Warsaw — 000bd00

#### Manual

- [x] 1.3 The journal feature still boots with the existing `/journal` write flow unchanged while the new read-side contracts remain additive — 000bd00
- [x] 1.4 The agreed reporting rules are explicit and reviewable: Europe/Warsaw boundaries, completed 7-day and 30-day periods, eight completed weekly buckets, and hundreds-scale bounded reads — 000bd00

### Phase 2: Private history browsing

#### Automated

- [x] 2.1 MVC tests cover `/journal` as the latest-10 recap and `/journal/history` as the paginated private archive
- [x] 2.2 History tests prove paging, newest-first ordering, and owner-only visibility in the UI layer

#### Manual

- [x] 2.3 A logged-in user can move from `/journal` to `/journal/history` and browse older entries without seeing another user's data
- [x] 2.4 `/journal` still shows only the latest 10 entries even when the user has a longer history available on the archive page

### Phase 3: Mood trends analytics

#### Automated

- [ ] 3.1 Service tests prove trend bucketing, current-week summary math, and gap-aware completed-period datasets
- [ ] 3.2 MVC tests render `/journal/trends` with the expected summary-card and chart-data shape for empty and populated users

#### Manual

- [ ] 3.3 `/journal/trends` shows stable 7-day and 30-day charts that do not include today's partial data
- [ ] 3.4 The current-week summary and the weekly chart both feel consistent with Europe/Warsaw calendar expectations and do not invent data for missing days

### Phase 4: Navigation and regression hardening

#### Automated

- [ ] 4.1 Auth and application regression cover saved-request redirects plus public-route stability across all journal surfaces
- [ ] 4.2 Full regression passes with history and trends coverage included

#### Manual

- [ ] 4.3 A user can sign in, write on `/journal`, browse `/journal/history`, and inspect `/journal/trends` without dead-end navigation
- [ ] 4.4 No page exposes another user's data or suggests unsupported drill-down, edit, or correction behavior

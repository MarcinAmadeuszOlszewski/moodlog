# Private history and mood trends — Plan Brief

> Full plan: `context/changes/history-and-mood-trends/plan.md`
> Research: `context/changes/history-and-mood-trends/research.md`

## What & Why

Extend the private journal so the user can do three distinct things: write on `/journal`, browse older entries on `/journal/history`, and review mood analytics on `/journal/trends`. This turns the current “latest entries” recap into a real private history + trends slice without reopening auth, persistence, or AI-classification scope.

## Starting Point

Today the app already has the write side for journaling: authenticated `/journal`, persisted entries, owner-scoped reads, and a latest-10 recent list (`src/main/java/com/amadeuszx/moodlog/JournalController.java:31-104`, `src/main/java/com/amadeuszx/moodlog/JournalEntryService.java:35-73`). What is missing is the read-model breadth: no paginated history, no trend DTOs, no date-window aggregation, and no dedicated trend UI.

## Desired End State

When this plan is done, `/journal` remains the write surface with the latest 10 entries, `/journal/history` becomes the paginated private archive, and `/journal/trends` shows summary cards plus charts for completed 7-day and 30-day periods and a weekly view. Trend calculations use effective mood, Europe/Warsaw boundaries, and honest sparse-data handling with gaps rather than invented values.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Page split | `/journal` + `/journal/history` + `/journal/trends` | Keeps writing, browsing, and analytics separate while still staying inside one journal area. | Plan |
| `/journal` scope | Latest 10 entries only | Preserves the current write flow and keeps the page from becoming an unbounded archive. | Plan |
| History scope | Dedicated paginated history page now | Satisfies the roadmap's “review private entry history” promise without overloading `/journal`. | Plan |
| Trend presentation | Chart.js charts plus summary cards | Best matches the product's “visible trends” promise without adding a new frontend stack. | Plan |
| Trend metric | Average score per bucket, dominant mood in summaries | Uses the existing score naturally while keeping cards human-readable. | Plan |
| Time semantics | Completed 7-day/30-day periods, Europe/Warsaw boundaries | Makes charts stable and user-facing dates natural for the MVP audience. | Plan |
| Weekly scope | Current-week summary plus 8 completed weekly buckets | Gives both “how am I doing now?” and a broader weekly pattern view. | Plan |
| Mood precedence | Effective mood = override if present, else system | Future-proofs S-03 so S-04 can change writes more than reads. | Plan |
| Aggregation strategy | Bounded Java-side aggregation over owner-scoped reads | Fits the current H2/PostgreSQL setup better than DB-specific date SQL. | Research |
| Drill-down | No entry-detail route in S-03 | Keeps the slice focused on history and trends instead of opening a new read-detail surface. | Plan |

## Scope

**In scope:**
- shared history/trend DTOs and read-side service methods
- `/journal/history` paginated private archive
- `/journal/trends` charts + summary cards
- Europe/Warsaw bucketing, effective mood, and sparse-data rules
- auth/privacy/navigation regression coverage for the expanded journal area

**Out of scope:**
- entry-detail page or clickable chart drill-down
- edit/delete/correct UI from S-04
- classification-failure persistence redesign
- per-user timezone settings
- optimization for very large datasets

## Architecture / Approach

Stay inside the current Spring MVC monolith. Extend `JournalEntryRepository` with paginated history and bounded date reads, assemble history/trend DTOs in `JournalEntryService`, and render dedicated Thymeleaf templates for `/journal/history` and `/journal/trends`. Charts are initialized from server-prepared data only; no client-side fetching or analytics logic.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Shared read-side foundation | Config, repository reads, effective mood, and DTO contracts | Date/week semantics can drift if not pinned down early |
| 2. Private history browsing | `/journal/history` plus `/journal` latest-10 contract | Easy to blur recap and archive responsibilities |
| 3. Mood trends analytics | `/journal/trends`, charts, summaries, and sparse-state behavior | Time-window math and chart semantics can regress quietly |
| 4. Navigation and regression hardening | Cross-page auth, navigation polish, and full regression coverage | Expanded private routing can break saved-request or privacy guarantees |

**Prerequisites:** existing S-02 journaling flow, the new research doc, and acceptance of fixed Europe/Warsaw timezone semantics for the MVP
**Estimated effort:** ~3-4 focused sessions across 4 phases

## Open Risks & Assumptions

- The plan assumes hundreds of entries per user, not tens of thousands.
- S-02's “block save on classification failure” decision remains untouched even though earlier product notes differed.
- The research pass preferred staying on `/journal`, but this plan intentionally accepts the extra routing scope for a clearer UX split.

## Success Criteria (Summary)

- A signed-in user can keep writing on `/journal`, browse older entries on `/journal/history`, and inspect trends on `/journal/trends`.
- History and trends remain owner-only, gap-aware, and consistent with Europe/Warsaw date/week boundaries.
- The expanded journal area keeps login redirects, public-route behavior, and privacy guarantees intact.

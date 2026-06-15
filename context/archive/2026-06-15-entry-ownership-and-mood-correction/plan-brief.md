# Entry Ownership and Mood Correction — Plan Brief

> Full plan: `context/changes/entry-ownership-and-mood-correction/plan.md`
> Research: `context/changes/entry-ownership-and-mood-correction/research.md`

## What & Why

Deliver S-04 (FR-004 + FR-006): users can delete their own journal entries, edit entry text with automatic mood reclassification, and manually correct the AI-assigned mood tag. In a private journal, ownership means being able to fix or remove your own entries — this slice closes the core mutation gap left after S-01/S-02/S-03.

## Starting Point

S-01/S-02/S-03 are complete. The DB schema already has nullable `override_mood_tag` and `override_mood_score` columns in `journal_entries` (added in V2). Four disabled ownership tests in `JournalEntryOwnershipTests` already define the HTTP surface. No controller endpoints, service mutation methods, or template controls exist yet.

## Desired End State

A logged-in user visits `/journal/history` and sees delete, edit, and mood-correction controls on each entry. Deleting prompts a JS confirmation then removes the entry. Editing opens a pre-filled form at `/journal/{id}/edit`, saves re-classify the text, and redirects back to history. Correcting mood shows a Polish-labeled dropdown (no UNKNOWN option) and updates the history label. Non-owner requests for any of these operations return 404.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
|---|---|---|---|
| Content editing in scope | Yes, full edit + delete + mood override | FR-004 says "edit or delete" — deliver the full intent | Plan |
| Mood form fields | Tag only, no score | Simpler UX; system score used as fallback for trend averages | Plan |
| Delete confirmation | JS `confirm()` before submit | Zero extra routes or templates; acceptable for a personal private journal | Plan |
| Placement of controls | History page only | History already has per-entry context; journal page is a compact excerpt list | Plan |
| Reclassify on content edit | Yes | Keeps mood tag accurate to the actual text; same fallback-to-UNKNOWN pattern as create | Plan |
| Edit form | Separate page (`GET /journal/{id}/edit`) | No JS needed; reuses existing form patterns | Plan |
| HTTP method surface | `DELETE` / `PATCH` / `PUT` via HiddenHttpMethodFilter | Matches the pre-written disabled test expectations exactly | Research |
| Ownership error code | 404 (not 403) | Avoids leaking entry existence to cross-user callers | Research |
| resolveEffectiveMood fix | Tag-only check; fall back to system score when override score is null | Tag-corrected entries stay in trend score averages | Plan |
| No new migration | Use existing override columns from V2 | Override columns were designed in from day one | Research |

## Scope

**In scope:**
- `DELETE /journal/{id}` — delete owned entry with JS confirm; redirect to history
- `GET /journal/{id}/edit` + `PUT /journal/{id}` — edit entry text; mood reclassified on save
- `PATCH /journal/{id}/mood` — tag-only mood correction (no UNKNOWN, no score field)
- `JournalHistoryItem.id` field + history template controls
- New `journal-edit.html` template
- Enable 4 disabled ownership tests; add 2 PUT ownership tests; add 4 route protection tests; add E2E flow tests
- Fix `resolveEffectiveMood` to accept tag-only override

**Out of scope:**
- Controls on journal.html (recent entries)
- Inline editing in history rows
- Score input in mood correction
- UNKNOWN as a selectable mood option
- Soft delete / undo
- Re-classification when mood override is applied (only when text is edited)

## Architecture / Approach

`HiddenHttpMethodFilter` bridges Thymeleaf `POST` forms (with `_method` hidden field) to `@DeleteMapping`, `@PatchMapping`, and `@PutMapping` in `JournalController`. Ownership is enforced in `JournalEntryService` via a combined `findByIdAndUserAccountId` query — not-found-or-wrong-owner throws `JournalEntryNotFoundException` (`@ResponseStatus(NOT_FOUND)`), which Spring resolves to 404 before the controller returns. `JournalEntry` gains two narrow mutation methods (`applyMoodOverride`, `updateContent`) for JPA dirty-checking; no raw setters.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. Domain Layer | Entity update methods, exception, repo query, service methods, `resolveEffectiveMood` fix, service tests | `updateEntryContent` reclassification path must mirror `saveEntry` fallback exactly |
| 2. Web Layer | Controller endpoints, HiddenHttpMethodFilter, 4 enabled + 6 new tests | PATCH ownership tests need `moodTag` param added or they return 400 instead of 404 |
| 3. Presentation Layer | `JournalHistoryItem.id`, history controls, edit template, E2E flow tests | Template form action URLs must match controller path variables exactly |

**Prerequisites:** S-03 complete (done). Local profile working (`application-local.properties` + H2).  
**Estimated effort:** ~3 focused sessions across 3 phases.

## Open Risks & Assumptions

- Local manual testing should run with the `local` profile pinned to the stub classifier. Under the stub, reclassification is deterministic by content keywords rather than fixed to a single mood, so the edit-flow check should use text that intentionally maps to a different tag.
- `resolveEffectiveMood` fix changes existing behavior: if any production data has `overrideMoodTag` set but `overrideMoodScore` null (not possible before S-04), behaviour changes. Safe assumption: no such data exists.

## Success Criteria (Summary)

- `.\mvnw.cmd test` passes at the end of each phase with no regressions.
- A logged-in user can delete, edit, and correct mood on their own history entries; non-owner requests return 404.
- The Polish mood label in history reflects any correction immediately after redirect.

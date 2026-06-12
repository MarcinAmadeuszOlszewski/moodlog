# AI Boundary Hardening — Plan Brief

> Full plan: `context/changes/testing-ai-boundary-hardening/plan.md`
> Research: `context/changes/testing-ai-boundary-hardening/research.md`

## What & Why

Implements the PRD NFR that classification failures must not lose the user's journal entry. Research confirmed the current `saveEntry()` blocks persistence on any classifier failure — the entry is never saved. The user's #1 fear ("entry silently lost") and the PRD requirement ("save with unknown mood") are both unmet. This change fixes that and hardens the classifier response-parsing contract.

## Starting Point

`JournalEntryService.saveEntry()` calls the classifier before building the entry; any `MoodClassificationFailedException` propagates, leaving 0 persisted entries. `MoodTag` has no `UNKNOWN` value, `system_mood_score` is NOT NULL in the DB, and `OpenAiMoodClassifier.OpenAiMoodResponse.moodScore` is a primitive `int` that silently defaults to 0 on a missing JSON field.

## Desired End State

A classification failure saves the entry with `MoodTag.UNKNOWN` and a null score, then redirects to `/journal` where the entry appears with "Nieznane" (no score). The classifier rejects a missing `moodScore` field as `INVALID_RESPONSE`. `.\mvnw.cmd test` passes with a test suite that reflects both contracts.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
|---|---|---|---|
| Fallback behavior | Implement PRD fallback — save with UNKNOWN mood | PRD NFR + user interview Q1 both require it | Research |
| Fallback score | null (nullable schema change) | Avoids storing a fabricated number; semantically clearest | Plan |
| Controller on fallback save | Redirect to /journal (success path) | PRD says "user still sees the saved entry" | Plan |
| moodScore in response record | Change `int` → `Integer`; null = INVALID_RESPONSE | Eliminates silent score=0 default for missing AI field | Research |
| Fallback catch location | In `saveEntry()`, not in `classifyContent()` | Keeps classifyContent a pure "classify or throw" function | Plan |
| Existing test updates | Rename + update (not keep old) | Two tests for conflicting behaviors creates confusion | Plan |
| UNKNOWN display | "Nieznane" with no score segment when null | Clean; no misleading numbers stored or displayed | Plan |
| Fallback logging | Log a warning on fallback save | Allows ops to monitor AI instability without re-reading user text | Plan |

## Scope

**In scope:**
- `MoodTag.UNKNOWN` enum value + Polish label "Nieznane"
- V3 Flyway migration (nullable `system_mood_score` + updated CHECK constraint)
- `JournalEntry`, `JournalTrendEntryProjection`, `JournalEntryListItem`, `JournalHistoryItem` — score type `int` → `Integer`
- `JournalEntryService.saveEntry()` fallback catch + warning log
- `OpenAiMoodClassifier.OpenAiMoodResponse.moodScore` `int` → `Integer` + null check
- `journal.html` and `journal-history.html` null-safe score display
- Updated service and flow tests; 4 new classifier unit tests; 1 new fallback flow test

**Out of scope:**
- Override-mood handling (S-04) — not yet built
- Trend-chart display for UNKNOWN entries — UNKNOWN entries are filtered from trend averages; chart is unaffected
- E2E Playwright tests
- `MoodClassification` record changes — it keeps `int moodScore` (only used on the happy path)

## Architecture / Approach

The fallback lives entirely in `JournalEntryService.saveEntry()`. `classifyContent()` remains "classify or throw." On catch, a private `saveEntryWithUnknownMood()` helper builds and persists the `JournalEntry` with UNKNOWN tag and null score, logs the warning, and returns normally — so the controller's POST-redirect-GET flow is unchanged. The `OpenAiMoodResponse` record tightening is independent and can be committed separately.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. Foundation | Nullable schema + `MoodTag.UNKNOWN` + display wiring; all existing tests still pass | V3 migration must be compatible with H2 PostgreSQL mode |
| 2. Service fallback + classifier fix | Fallback fires; classifier rejects null score; 2 existing tests intentionally break | `final` variable rule requires careful saveEntry() restructure |
| 3. Test updates + new tests | Full green suite; all 8 classifier parser paths covered | Mocking `ChatResponse` chain in new unit tests needs `Mockito.mock()` chaining |

**Prerequisites:** Phase 1 must land before Phase 2 (entity/enum changes compile before service uses them).
**Estimated effort:** ~2 focused sessions across 3 phases.

## Open Risks & Assumptions

- H2 PostgreSQL mode supports `ALTER TABLE … ALTER COLUMN … DROP NOT NULL` — confirmed by existing UUID and `timestamp with time zone` usage in V1/V2 migrations.
- `classifiedAt` and `classifierProvider`/`classifierModel` are `NOT NULL` on the entity; the fallback must populate them. Provider/model come from the exception's `getProvider()`/`getModel()` (normalised to `"unknown"` by the exception ctor if blank).
- The Spring AI `ChatResponse`, `Generation`, and `AssistantMessage` types are not easily constructible without a mock framework — new classifier tests use `Mockito.mock()` chain.

## Success Criteria (Summary)

- `.\mvnw.cmd test` passes with zero failures after Phase 3.
- A classification failure on the running app redirects to `/journal` and shows the entry with "Nieznane" and no score segment.
- Application logs show `journal.classification.failure` and `journal.entry.saved.with.unknown.mood` on failure path with no user text in either line.

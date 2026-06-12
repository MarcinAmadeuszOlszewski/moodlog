# AI Boundary Hardening — Phase 1 Test Rollout Implementation Plan

## Overview

Implements the PRD NFR ("a failed mood-classification must not prevent the entry from being saved") and hardens the `OpenAiMoodClassifier` response-parsing contract. Phase 1 of the test-plan rollout requires both implementation work (the fallback path does not exist yet) and new/updated tests.

## Current State Analysis

- `JournalEntryService.saveEntry()` calls `classifyContent()` before building the entry. Any `MoodClassificationFailedException` propagates — the entry is never persisted.
- `MoodTag` has no `UNKNOWN` value. `polishMoodLabel()` is an exhaustive switch with no default case.
- `JournalEntry.systemMoodScore` is `int` with `NOT NULL` DB constraint and a check `BETWEEN 0 AND 100`.
- `OpenAiMoodClassifier.OpenAiMoodResponse` uses `int moodScore` (primitive). A missing `moodScore` field in the AI JSON silently defaults to 0 — a valid value that bypasses all validation.
- `OpenAiMoodClassifierTests` covers 3 of 7 parser branches; 4 paths (blank text, null result, out-of-range score, missing moodScore) are untested.
- `JournalEntryRepository.JournalTrendEntryProjection.getSystemMoodScore()` returns `int`.
- `JournalEntryListItem` and `JournalHistoryItem` records carry `int moodScore`.
- Both `application.properties` and test properties use `H2 MODE=PostgreSQL`; Flyway migrations run on H2 in tests.

## Desired End State

`saveEntry()` catches `MoodClassificationFailedException`, persists the entry with `MoodTag.UNKNOWN` and a null score, logs a warning, and returns the saved entry. The controller redirects to `/journal` as on a successful classification. Journal and history pages display "Nieznane" with no score segment for UNKNOWN entries. All eight classifier-parser branches are covered by unit tests. `.\mvnw.cmd test` passes.

### Key Discoveries

- `JournalEntry` constructor (`JournalEntry.java:66`) takes `int systemMoodScore` — must change to `Integer`.
- `system_mood_score` DB column has a NOT NULL constraint and `CHECK (system_mood_score BETWEEN 0 AND 100)`; both must be relaxed to allow NULL (`V3__nullable_system_mood_score.sql`).
- `JournalTrendEntryProjection.getSystemMoodScore()` (`JournalEntryRepository.java:20`) returns `int` — must change to `Integer`.
- `toReportedEntry()` in `JournalEntryService` maps every entry to a `ReportedJournalEntry` for trend calculations; UNKNOWN entries (null score) must be filtered from the trend stream.
- `classifiedAt`, `classifierProvider`, and `classifierModel` are `NOT NULL` on `journal_entries` — the fallback entry must populate them. Use `Instant.now(clock)`, and `exception.getProvider()` / `exception.getModel()` (both normalised to `"unknown"` if blank by `MoodClassificationFailedException`).
- journal.html line 51 and journal-history.html line 37 concatenate `entry.moodScore` into the label string unconditionally — will NPE when score is null; requires Thymeleaf null guard.
- `OpenAiMoodClassifier.classify()` wraps any `RuntimeException` from parsing as `INVALID_RESPONSE` (line 62–64). Unboxing a null `Integer` to `int` inside `new MoodClassification(...)` would trigger this path implicitly, but an explicit null check is required for clarity and tested behaviour.

## What We're NOT Doing

- No change to `MoodClassification` record — it keeps `int moodScore` (0–100 validated) since `MoodClassification` is only produced on the happy path.
- No change to trend-calculation averaging logic — UNKNOWN entries are filtered out before `averageMoodScore()` is called, so that method remains simple.
- No override-mood handling for UNKNOWN — `overrideMoodTag`/`overrideMoodScore` remain for future S-04.
- No UI indicator or flash message for the fallback save — the PRD says "user sees the saved entry," which is satisfied by the redirect to `/journal`.
- No E2E Playwright tests — out of scope for this phase.

## Implementation Approach

Three sequential phases: (1) schema + enum + entity + display wiring — all existing tests must continue to pass after this phase; (2) service fallback + classifier tightening — new production behaviour; (3) test updates + new tests — pins all contracts.

## Critical Implementation Details

**`saveEntry()` variable assignment constraint**: AGENTS.md requires `final` on local variables unless reassigned. `MoodClassification moodClassification` must be assigned inside a `try` block, which prevents `final`. The solution: assign without `final`, use an early `return` inside the `catch` to call a private `saveEntryWithUnknownMood(...)` helper — keeping the happy-path code below the try/catch clean and all remaining locals `final`.

**H2 PostgreSQL mode**: Both test (`MODE=PostgreSQL`) and dev properties use H2 with PostgreSQL mode. The V3 migration must use only SQL that H2 in PostgreSQL mode supports. `ALTER TABLE … ALTER COLUMN … DROP NOT NULL` is supported in this mode. Write one migration script; it runs on both H2 and real PostgreSQL.

---

## Phase 1: Foundation — nullable schema, MoodTag.UNKNOWN, display wiring

### Overview

Makes `system_mood_score` nullable at every layer (DB → entity → projection → DTOs → templates) and adds `MoodTag.UNKNOWN` with its Polish label. Goal: existing tests still pass after this phase lands; the app compiles and Hibernate schema validation succeeds.

### Changes Required

#### 1. Flyway migration V3

**File**: `src/main/resources/db/migration/V3__nullable_system_mood_score.sql`

**Intent**: Relax the NOT NULL constraint and the range CHECK on `system_mood_score` so the column can hold NULL for UNKNOWN entries.

**Contract**: Three statements — drop the old check constraint, drop the NOT NULL, add the updated check:

```sql
alter table journal_entries
    drop constraint ck_journal_entries_system_mood_score_range;

alter table journal_entries
    alter column system_mood_score drop not null;

alter table journal_entries
    add constraint ck_journal_entries_system_mood_score_range
    check (system_mood_score is null or system_mood_score between 0 and 100);
```

#### 2. MoodTag enum

**File**: `src/main/java/com/amadeuszx/moodlog/classification/MoodTag.java`

**Intent**: Add `UNKNOWN` as the last enum constant so callers can represent "classification failed."

**Contract**: Append `UNKNOWN` after `OVERWHELMED`. The exhaustive switch in `polishMoodLabel()` will fail to compile until updated in change 5 below — complete both changes in the same commit.

#### 3. JournalEntry entity

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntry.java`

**Intent**: Allow a null system mood score, matching the updated DB column.

**Contract**:
- `@Column(name = "system_mood_score", nullable = false)` → remove `nullable = false` (defaults to true)
- Field type: `int systemMoodScore` → `Integer systemMoodScore`
- Constructor parameter: `int systemMoodScore` → `Integer systemMoodScore`
- Getter: `int getSystemMoodScore()` → `Integer getSystemMoodScore()`

#### 4. JournalEntryRepository projection

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryRepository.java`

**Intent**: Make the trend projection's score getter return a nullable Integer to match the entity.

**Contract**: `int getSystemMoodScore()` → `Integer getSystemMoodScore()` inside `JournalTrendEntryProjection`.

#### 5. JournalEntryListItem and JournalHistoryItem

**Files**:
- `src/main/java/com/amadeuszx/moodlog/journal/history/JournalEntryListItem.java`
- `src/main/java/com/amadeuszx/moodlog/journal/history/JournalHistoryItem.java`

**Intent**: Let the moodScore field carry null for UNKNOWN entries so templates can conditionally render it.

**Contract**: Change `int moodScore` → `Integer moodScore` in both records.

#### 6. JournalEntryService — polishMoodLabel, EffectiveMood, trend filtering

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java`

**Intent**: Four coordinated changes to handle null scores throughout the service:

- `polishMoodLabel()`: add `case UNKNOWN -> "Nieznane"` to the switch expression (required to compile after MoodTag.UNKNOWN is added).
- `EffectiveMood` private record: `int moodScore` → `Integer moodScore`.
- `toReportedEntry()`: add a null guard — if `effectiveMood.moodScore()` is null, return `null` (UNKNOWN entries carry no trend signal and should not appear in averages).
- `getTrendView()`: add `.filter(Objects::nonNull)` to the stream that maps repository projections to `ReportedJournalEntry` instances.

**Contract**: `ReportedJournalEntry` private record keeps `int moodScore` (non-null) because only entries that pass the null guard reach it. `averageMoodScore()` is unchanged.

#### 7. journal.html and journal-history.html templates

**Files**:
- `src/main/resources/templates/journal.html`
- `src/main/resources/templates/journal-history.html`

**Intent**: Avoid NPE and display "Nieznane" without a score segment when `entry.moodScore` is null.

**Contract**: Replace the mood-score line in each template:

In `journal.html` (line 51):
```html
<p th:text="${entry.moodScore != null ? entry.moodLabel + ' — ' + entry.moodScore + '/100' : entry.moodLabel}">Spokój — 73/100</p>
```

In `journal-history.html` (line 37):
```html
<p th:text="${entry.moodScore != null ? entry.moodLabel + ' — ' + entry.moodScore + '/100' : entry.moodLabel}">Spokój — 73/100</p>
```

### Success Criteria

#### Automated Verification

- `.\mvnw.cmd test` passes — all existing tests pass; context loads with the new schema; Hibernate validate mode succeeds against the updated columns; `ApplicationTests.journalEntrySchemaBootstrapsUnderTestConfiguration` passes.
- Migration V3 applies cleanly under H2 (MODE=PostgreSQL); no Flyway checksum errors.

#### Manual Verification

- App starts locally; no Hibernate schema-validation error in logs.
- Creating a test entry on the running app via a happy-path POST still shows "Spokój — 74/100" (or equivalent) in the recent-entry list.

**Implementation Note**: After all automated checks pass and the app starts cleanly, pause for manual confirmation before Phase 2.

---

## Phase 2: Service fallback + OpenAiMoodClassifier tightening

### Overview

Adds the entry-durability fallback in `saveEntry()` and tightens the classifier's response-parsing contract. After this phase, a classification failure saves the entry with UNKNOWN mood and a null score, redirecting to `/journal` as on success.

### Changes Required

#### 1. JournalEntryService.saveEntry() — fallback path

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java`

**Intent**: When `classifyContent()` throws `MoodClassificationFailedException`, save the entry with `MoodTag.UNKNOWN`, a null score, the provider/model from the exception, and a log warning — then return the saved entry normally. The controller's existing `catch` block is unreachable after this change (no exception propagates from `saveEntry()` on classification failure) but remains harmless until tests are updated in Phase 3.

**Contract**: Introduce a `MoodClassification moodClassification;` variable (not `final` — it is assigned inside a try block). Wrap the call to `classifyContent()` in a `try` block; in the `catch (MoodClassificationFailedException)`, delegate to a new private helper `saveEntryWithUnknownMood(userAccount, content, safeUserIdentifier, exception)` and return its result early. The happy path after the try/catch remains fully `final`.

The helper builds a `JournalEntry` with:
- `MoodTag.UNKNOWN`
- `null` systemMoodScore
- `exception.getProvider()` and `exception.getModel()` for classifier metadata
- `Instant.now(clock)` for `classifiedAt`

After saving, it logs: `journal.entry.saved.with.unknown.mood identifier={} provider={} model={}`.

#### 2. OpenAiMoodClassifier — Integer moodScore + explicit null check

**File**: `src/main/java/com/amadeuszx/moodlog/classification/OpenAiMoodClassifier.java`

**Intent**: Treat a missing `moodScore` field in the AI JSON as `INVALID_RESPONSE` rather than silently defaulting to 0.

**Contract**: Change the private record `OpenAiMoodResponse(MoodTag moodTag, int moodScore)` to `OpenAiMoodResponse(MoodTag moodTag, Integer moodScore)`. After the existing `openAiMoodResponse == null` check (line 47), add: if `openAiMoodResponse.moodScore() == null`, throw `buildFailureException(MoodClassificationFailureReason.INVALID_RESPONSE)`. Proceed to construct `MoodClassification` using `openAiMoodResponse.moodScore()` (unboxing now safe after the null guard).

### Success Criteria

#### Automated Verification

- `.\mvnw.cmd test` — existing service tests `blocksPersistenceWhenTheClassifierFails` and `classificationFailurePreservesTheSubmittedText` will now FAIL (expected — they assert 0L entries; the fallback now saves 1L). These failures are intentional signals that Phase 3 test updates are needed.
- `OpenAiMoodClassifierTests` still passes (no change to classifier error-mapping; null moodScore path tested in Phase 3).
- Context loads; `ApplicationTests` passes.

#### Manual Verification

- Trigger a classification failure by temporarily setting `moodlog.ai.enabled=false` and `moodlog.ai.provider=invalid` in local dev, then POST an entry. App redirects to `/journal`; entry appears in the list with "Nieznane" and no score.
- Application log shows both `journal.classification.failure` and `journal.entry.saved.with.unknown.mood` lines (no entry text in either line).

**Implementation Note**: The intentional test failures in `blocksPersistenceWhenTheClassifierFails` and `classificationFailurePreservesTheSubmittedText` confirm the fallback is wired. Pause here; Phase 3 corrects the tests.

---

## Phase 3: Test updates and new tests

### Overview

Updates tests whose assertions reflect the old "block-on-failure" contract, and adds unit and integration tests for all newly-covered paths. After this phase, `.\mvnw.cmd test` passes completely and the test suite accurately reflects the implemented contracts.

### Changes Required

#### 1. JournalEntryServiceTests — update two tests

**File**: `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryServiceTests.java`

**Intent**: Rename and update `blocksPersistenceWhenTheClassifierFails` and `rejectsInvalidClassificationPayloadsBeforePersistence` to assert the new fallback contract.

**Contract**:
- `blocksPersistenceWhenTheClassifierFails` → rename to `savesEntryWithUnknownMoodWhenClassifierFails`. Assert: `journalEntryRepository.count() == 1`; loaded entry has `systemMoodTag == MoodTag.UNKNOWN`; `getSystemMoodScore() == null`.
- `rejectsInvalidClassificationPayloadsBeforePersistence` → rename to `savesEntryWithUnknownMoodWhenClassificationPayloadIsInvalid`. Same assertions: count = 1, UNKNOWN tag, null score.

#### 2. JournalFlowTests — update two tests

**File**: `src/test/java/com/amadeuszx/moodlog/journal/JournalFlowTests.java`

**Intent**: Update `classificationFailurePreservesTheSubmittedText` and `classificationFailureDoesNotExposeJournalTextInLogs` to assert the redirect + saved entry outcome.

**Contract**:
- `classificationFailurePreservesTheSubmittedText` → rename to `savesEntryWithUnknownMoodAndRedirectsWhenClassificationFails`. Assert: redirect to `/journal`; `journalEntryRepository.count() == 1`.
- `classificationFailureDoesNotExposeJournalTextInLogs`: update to expect a redirect (not `view().name("journal")`); keep the log-safety assertions (`assertFalse(output.getOut().contains(entryText))`).

#### 3. JournalFlowTests — new fallback end-to-end test

**File**: `src/test/java/com/amadeuszx/moodlog/journal/JournalFlowTests.java`

**Intent**: Prove the full fallback flow end-to-end: classifier fails → entry saved → redirect → entry visible in journal with "Nieznane" label and no score string.

**Contract**: New `@Test` `@DisplayName("shows unknown mood entry in recent list after classification failure")`. Use `@MockitoBean MoodClassifier`, have it throw `MoodClassificationFailedException`. POST to `/journal`; assert redirect to `/journal`. GET `/journal`; assert: entry excerpt appears in HTML, string "Nieznane" appears, string "/100" does NOT appear in the entry row.

#### 4. OpenAiMoodClassifierTests — four new tests

**File**: `src/test/java/com/amadeuszx/moodlog/classification/OpenAiMoodClassifierTests.java`

**Intent**: Cover the four parser paths not exercised by existing tests.

**Contract**: Four new `@Test` methods, each using `@ExtendWith(MockitoExtension.class)` and `@Mock OpenAiChatModel`:

- `@DisplayName("maps non-null response with null result to an invalid response reason")`: mock `call()` to return a non-null `ChatResponse` whose `getResult()` returns null. Assert `INVALID_RESPONSE`.

- `@DisplayName("maps blank provider response text to an invalid response reason")`: mock to return a `ChatResponse` with a non-null result/output where `getText()` returns `""`. Assert `INVALID_RESPONSE`.

- `@DisplayName("maps out-of-range mood score to an invalid response reason")`: mock to return a `ChatResponse` with text `{"moodTag":"CALM","moodScore":150}`. Assert `INVALID_RESPONSE`.

- `@DisplayName("maps missing mood score field to an invalid response reason")`: mock to return a `ChatResponse` with text `{"moodTag":"CALM"}` (no `moodScore` field). Assert `INVALID_RESPONSE`.

**Note on mock ChatResponse construction**: Use `Mockito.mock(ChatResponse.class)` with `when(...).thenReturn(...)` chaining to build the response chain (`response.getResult().getOutput().getText()`). The Spring AI `ChatResponse`, `Generation`, and `AssistantMessage` types are not easily constructible directly.

### Success Criteria

#### Automated Verification

- `.\mvnw.cmd test` passes with zero failures.
- All renamed tests pass with new assertions.
- All four new `OpenAiMoodClassifierTests` pass.
- New fallback flow test passes.
- Existing `ApplicationTests`, `AuthenticationFlowTests`, `JournalTrendsFlowTests`, `UserAccountServiceTests`, `UserAccountRepositoryTests`, and `JournalEntryRepositoryTests` are unaffected and pass.

#### Manual Verification

- POST a journal entry when the AI is unavailable (stub configured); entry appears in the list with "Nieznane" and no "/100" text.
- POST a journal entry with a real AI key (integration environment); entry appears with a real mood label and score.

---

## Testing Strategy

### Unit Tests

- `OpenAiMoodClassifierTests`: 3 existing + 4 new = 7 tests covering the full `classify()` parser pipeline.

### Integration Tests

- `JournalEntryServiceTests`: updated tests assert UNKNOWN fallback path; existing happy-path and log-safety tests unchanged.
- `JournalFlowTests`: updated and new tests covering: validation failure, happy path, classification failure (fallback), log safety, data isolation, pagination, history paging.
- `ApplicationTests`: context-load smoke tests; no changes needed.

### Manual Testing Steps

1. Start app locally with `moodlog.ai.enabled=false` (stub default). POST an entry. Verify redirect to `/journal` and entry appears with "Nieznane" and no score.
2. Start app locally with `moodlog.ai.enabled=true` and a valid `OPENAI_API_KEY`. POST an entry. Verify entry appears with a real mood label and score (e.g., "Spokój — 74/100").
3. Check the log for `journal.classification.failure` and `journal.entry.saved.with.unknown.mood` lines on failure path; verify no user text appears in either.

## Migration Notes

V3 migration is additive (relaxes constraints, does not change data). Existing rows with non-null scores are unaffected. Safe to deploy with a rolling update. No backfill required.

## References

- Research: `context/changes/testing-ai-boundary-hardening/research.md`
- Test plan Phase 1: `context/foundation/test-plan.md` §3
- V2 migration (reference for syntax): `src/main/resources/db/migration/V2__create_journal_entries.sql`
- Existing flow test pattern: `src/test/java/com/amadeuszx/moodlog/journal/JournalFlowTests.java`
- Existing classifier test pattern: `src/test/java/com/amadeuszx/moodlog/classification/OpenAiMoodClassifierTests.java`

---

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Foundation — nullable schema, MoodTag.UNKNOWN, display wiring

#### Automated

- [x] 1.1 `.\mvnw.cmd test` passes with all existing tests green and V3 migration applied — a0d3274
- [x] 1.2 Hibernate schema validation succeeds (no validate-mode errors on startup) — a0d3274

#### Manual

- [x] 1.3 App starts locally without schema validation errors; happy-path entry POST shows mood label with score — a0d3274

### Phase 2: Service fallback + OpenAiMoodClassifier tightening

#### Automated

- [x] 2.1 Context loads and `ApplicationTests` passes — fafbbdc
- [x] 2.2 `OpenAiMoodClassifierTests` passes (all 3 existing tests) — fafbbdc
- [x] 2.3 `blocksPersistenceWhenTheClassifierFails` and `classificationFailurePreservesTheSubmittedText` fail (expected signal that fallback fires) — fafbbdc

#### Manual

- [x] 2.4 Classification failure on local app redirects to `/journal`; entry visible with "Nieznane" and no score; log shows both failure and fallback-save lines without user text — fafbbdc

### Phase 3: Test updates and new tests

#### Automated

- [x] 3.1 `.\mvnw.cmd test` passes with zero failures (all renamed, updated, and new tests green) — e6382e7
- [x] 3.2 All four new `OpenAiMoodClassifierTests` pass — e6382e7
- [x] 3.3 New fallback end-to-end flow test passes — e6382e7

#### Manual

- [x] 3.4 Full manual smoke: stub path shows "Nieznane" with no score; real AI path shows mood label with score; log is clean of user text on both paths — e6382e7

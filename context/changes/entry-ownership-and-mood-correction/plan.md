# Entry Ownership and Mood Correction Implementation Plan

## Overview

Deliver S-04: users can delete their own journal entries, edit entry text (with automatic mood reclassification), and manually correct the AI-assigned mood tag. Builds on top of the complete S-01/S-02/S-03 stack.

## Current State Analysis

The DB schema already has `override_mood_tag` and `override_mood_score` nullable columns in `journal_entries` — no migration needed. Four disabled integration tests in `JournalEntryOwnershipTests` define the ownership contract exactly: `DELETE /journal/{id}` and `PATCH /journal/{id}/mood`, non-owner → 404, owner → 3xx. No edit/delete controller endpoints, service methods, or templates exist yet. `JournalHistoryItem` has no `id` field. `JournalEntry` is immutable (no setters). `resolveEffectiveMood` currently requires both `overrideMoodTag != null && overrideMoodScore != null` — this must change for tag-only correction to take effect.

## Desired End State

A logged-in user can open their entry history and delete any of their own entries (with a JS confirmation prompt), click "Edytuj" to reach a dedicated edit form pre-filled with the current text, save the edited text (which triggers mood reclassification), and click a mood button on any history entry to select a corrected mood tag from a dropdown. All three mutations reject non-owner requests with 404.

### Key Discoveries

- `JournalEntryOwnershipTests` (`src/test/java/.../journal/JournalEntryOwnershipTests.java`) — 4 disabled tests define the HTTP surface and expected status codes; PATCH tests send no `moodTag` param and must be updated when enabled.
- `resolveEffectiveMood` (`JournalEntryService.java:422`) — checks `overrideMoodTag != null && overrideMoodScore != null`; must be relaxed to `overrideMoodTag != null` with score falling back to `systemMoodScore` when `overrideMoodScore` is null, so tag-only corrections are included in trend score averages.
- `JournalEntry` domain constructor (`JournalEntry.java:68`) — accepts all fields at construction, no setters; update methods must be added to the entity for JPA dirty-checking.
- `JournalEntryService.saveEntry` (`JournalEntryService.java:72`) — reuse the same `classifyContent` + fallback-to-UNKNOWN pattern for `updateEntryContent`.
- `MoodClassificationFailedException` wraps provider/model metadata (`MoodClassificationFailedException.java`) — same fallback path applies on edit.
- `JournalHistoryItem` (`JournalHistoryItem.java`) — currently 5 fields, no `id`; must add `UUID id` as the first component.

## What We're NOT Doing

- No score input in the mood correction form — tag only; trend averages use system score as fallback.
- No mood correction or delete controls on `journal.html` (recent entries) — history page only.
- No inline editing in the history list — separate `/journal/{id}/edit` page.
- No re-classification when override mood is applied — only when entry text is edited.
- No undo / soft delete — deletion is immediate and permanent.
- No confirmation page (server-side) — JS `confirm()` only.
- No content edit on classification failure prevention — same fallback-to-UNKNOWN applies.
- `UNKNOWN` is not a selectable mood tag in the correction dropdown.

## Implementation Approach

Three sequential phases: domain layer first (entity + repository + service + service tests), then web layer (controller endpoints + filter + ownership/route tests), then presentation layer (DTOs + templates + E2E tests). Each phase has its own passing test suite before the next begins.

`HiddenHttpMethodFilter` bridges Thymeleaf POST forms to the `DELETE` / `PATCH` / `PUT` controller mappings that the disabled ownership tests already expect.

## Critical Implementation Details

**`resolveEffectiveMood` score fallback** — The entity-level override will store only `overrideMoodTag` (non-null) with `overrideMoodScore = null`. `resolveEffectiveMood` must return `EffectiveMood(overrideMoodTag, systemMoodScore)` when `overrideMoodTag != null` and `overrideMoodScore == null`, not null. Without this, tag-corrected entries drop out of trend score calculations silently.

**PATCH ownership tests need `moodTag` param** — Both the 404 (non-owner) and 3xx (owner) PATCH tests must include `.param("moodTag", "CALM")` after they are enabled. Without it, Spring resolves the missing required `@RequestParam` as 400 before the ownership check can fire, breaking both test expectations.

**`updateEntryContent` clears existing override** — When a user edits entry text, the system re-classifies and updates all system mood fields. Any previously set `overrideMoodTag` must be cleared so the new system result becomes the effective mood. The entity update method enforces this.

---

## Phase 1: Domain Layer

### Overview

Add mutability to the entry entity (update methods), introduce the not-found exception, add the ownership-scoped repository query, extend the service with delete / mood-override / content-edit / get-for-edit methods, fix `resolveEffectiveMood`, and cover all new paths with service-level tests.

### Changes Required

#### 1. JournalEntry — add mutation methods

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntry.java`

**Intent**: Enable JPA dirty-checking for the two mutation flows — mood override and content edit — without exposing raw setters.

**Contract**: Add two public void methods on the entity. `applyMoodOverride(MoodTag overrideMoodTag, Instant updatedAt)` sets `this.overrideMoodTag`, sets `this.overrideMoodScore = null`, and sets `this.updatedAt`. `updateContent(String content, MoodTag systemMoodTag, Integer systemMoodScore, String classifierProvider, String classifierModel, Instant classifiedAt, Instant updatedAt)` replaces all system classification fields, clears both override fields to null, and sets `this.updatedAt`. No Lombok `@Setter` — these are the only mutation surface.

---

#### 2. JournalEntryNotFoundException

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryNotFoundException.java`

**Intent**: Represent the case where an entry does not exist in the caller's journal — covers both "not found at all" and "belongs to another user." Returns 404, not 403, to avoid leaking entry existence.

**Contract**: `@ResponseStatus(HttpStatus.NOT_FOUND)` on the class. Single string-message constructor. Extends `RuntimeException`.

---

#### 3. JournalEntryRepository — ownership-scoped lookup

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryRepository.java`

**Intent**: Provide a single-query ownership check that finds an entry only when the caller's `userAccountId` matches, returning empty optional otherwise.

**Contract**: Add derived query method `Optional<JournalEntry> findByIdAndUserAccountId(UUID id, UUID userAccountId)`.

---

#### 4. JournalEntryService — fix resolveEffectiveMood

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java`

**Intent**: Let tag-only corrections take effect in history labels and trend calculations. Currently requires both override fields non-null; must relax to tag-only.

**Contract**: Change the condition in both `resolveEffectiveMood` overloads (lines 422 and 430) from `overrideMoodTag != null && overrideMoodScore != null` to `overrideMoodTag != null`. When using the entity overload: return `EffectiveMood(overrideMoodTag, overrideMoodScore != null ? overrideMoodScore : systemMoodScore)`. Apply the same score-fallback logic to the projection overload.

---

#### 5. JournalEntryService — new mutation methods

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java`

**Intent**: Expose delete, mood-override, content-edit, and get-for-edit operations that enforce ownership before any mutation.

**Contract**:

- `@Transactional public void deleteEntry(String userEmail, UUID entryId)` — resolves `UserAccount` from email; calls `findByIdAndUserAccountId`; throws `JournalEntryNotFoundException` if empty; calls `journalEntryRepository.delete(entry)`.

- `@Transactional public void applyMoodOverride(String userEmail, UUID entryId, MoodTag moodTag)` — same ownership pattern; calls `entry.applyMoodOverride(moodTag, Instant.now(clock))`; saves; logs with `safeEmailIdentifier` (no mood tag in logs).

- `@Transactional public JournalEntry updateEntryContent(String userEmail, UUID entryId, String newContent)` — ownership check; attempts `classifyContent(newContent, safeIdentifier)` via the existing private helper; on success calls `entry.updateContent(newContent, classification.moodTag(), classification.moodScore(), provider, model, classifiedAt, Instant.now(clock))`; on `MoodClassificationFailedException` calls `entry.updateContent(newContent, MoodTag.UNKNOWN, null, exceptionProvider, exceptionModel, Instant.now(clock), Instant.now(clock))`; saves and returns entry.

- `public JournalEntryEditView getEntryForEdit(String userEmail, UUID entryId)` — ownership check; returns `new JournalEntryEditView(entry.getId(), entry.getContent())`.

---

#### 6. New record types

**Files**:
- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryEditView.java`
- `src/main/java/com/amadeuszx/moodlog/journal/MoodTagOption.java`

**Intent**: `JournalEntryEditView` carries the pre-fill data for the edit form. `MoodTagOption` pairs a `MoodTag` with its Polish display label so templates do not need access to the service-internal `polishMoodLabel` switch.

**Contract**: Both are Java records. `JournalEntryEditView(UUID id, String content)`. `MoodTagOption(MoodTag tag, String polishLabel)`.

---

#### 7. JournalEntryService — selectableMoodOptions

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java`

**Intent**: Provide the controller with a filtered, labeled list of mood tags for the correction dropdown — no `UNKNOWN`, Polish labels.

**Contract**: `public List<MoodTagOption> selectableMoodOptions()` — returns all `MoodTag` values except `UNKNOWN`, each wrapped as `MoodTagOption(tag, polishMoodLabel(tag))`. No DB call; pure transformation.

---

#### 8. Service unit tests

**File**: `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryServiceTests.java`

**Intent**: Extend the existing service test class with coverage for each new method and the `resolveEffectiveMood` behaviour change.

**Contract**: Add camelCase test methods with `@DisplayName` and Lombok `val` for locals (per AGENTS.md). Cover:
- `deleteEntry` removes the entry when called by owner.
- `deleteEntry` throws `JournalEntryNotFoundException` when called by non-owner.
- `applyMoodOverride` sets `overrideMoodTag`; subsequent `getHistoryEntries` returns the corrected label.
- `applyMoodOverride` throws `JournalEntryNotFoundException` for non-owner.
- `resolveEffectiveMood` returns corrected tag with system score when tag-only override is applied.
- `updateEntryContent` re-classifies and clears override; `updatedAt` advances.
- `updateEntryContent` falls back to UNKNOWN + null score when classifier throws; entry is still saved.
- `updateEntryContent` throws `JournalEntryNotFoundException` for non-owner.

### Success Criteria

#### Automated Verification

- `.\mvnw.cmd test` passes (all existing + new service tests green).
- `JournalEntryNotFoundException` carries `@ResponseStatus(NOT_FOUND)`.
- `findByIdAndUserAccountId` exists in `JournalEntryRepository`.
- `selectableMoodOptions()` returns 7 options (all `MoodTag` values minus `UNKNOWN`).

#### Manual Verification

- Run `.\mvnw.cmd spring-boot:run` (local profile); confirm the app still starts and existing journal flows work unchanged.

**Pause here for manual confirmation before Phase 2.**

---

## Phase 2: Web Layer

### Overview

Enable `HiddenHttpMethodFilter`, add the four new controller endpoints, map `JournalEntryNotFoundException` to 404, enable and fix the four disabled ownership tests, add new ownership tests for `PUT`, and extend route protection tests to cover the new routes.

### Changes Required

#### 1. Enable HiddenHttpMethodFilter and pin the local classifier

**Files**:
- `src/main/resources/application.properties`
- `src/main/resources/application-local.properties`

**Intent**: Allow Thymeleaf `POST` forms to emit `DELETE`, `PATCH`, and `PUT` by including a `_method` hidden field, and make local manual verification deterministic without depending on a live model endpoint.

**Contract**: Add `spring.mvc.hiddenmethod.filter.enabled=true`. In `application-local.properties`, pin mood classification to the stub path (`moodlog.ai.enabled=false` or `moodlog.ai.provider=stub` — pick one and keep it consistent with `MoodClassifierConfiguration`) so local edit/reclassification checks are deterministic by content.

---

#### 2. JournalController — four new handlers

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalController.java`

**Intent**: Expose the four new HTTP operations and wire them to the service. All redirect after success (PRG); ownership failures propagate as `JournalEntryNotFoundException` which Spring resolves to 404 via `@ResponseStatus`.

**Contract**:

- `@DeleteMapping("/journal/{id}") public String deleteEntry(@PathVariable UUID id, Authentication auth)` — calls `journalEntryService.deleteEntry(auth.getName(), id)`; returns `"redirect:/journal/history"`.

- `@PatchMapping("/journal/{id}/mood") public String correctMood(@PathVariable UUID id, @RequestParam MoodTag moodTag, Authentication auth)` — calls `journalEntryService.applyMoodOverride(auth.getName(), id, moodTag)`; returns `"redirect:/journal/history"`.

- `@GetMapping("/journal/{id}/edit") public String showEditForm(@PathVariable UUID id, Authentication auth, Model model)` — calls `journalEntryService.getEntryForEdit(auth.getName(), id)`; passes the result into a private `populateEditModel(String userEmail, UUID entryId, JournalEntryForm form, Model model)` helper that sets `entryId`, the pre-filled `journalEntryForm`, `journalMaxContentLength`, and `userEmail`; returns `"journal-edit"`.

- `@PutMapping("/journal/{id}") public String updateEntry(@PathVariable UUID id, @Valid @ModelAttribute("journalEntryForm") JournalEntryForm form, BindingResult binding, Authentication auth, Model model)` — if `binding.hasErrors()` calls the same `populateEditModel(auth.getName(), id, form, model)` helper and returns `"journal-edit"` (re-render); otherwise calls `journalEntryService.updateEntryContent(auth.getName(), id, form.getContent())`; returns `"redirect:/journal/history"`.

---

#### 3. JournalController — add selectableMoodOptions to history model

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalController.java`

**Intent**: Provide the history template with the labeled, filtered mood list for the correction dropdown.

**Contract**: In the `GET /journal/history` handler, add `model.addAttribute("selectableMoodOptions", journalEntryService.selectableMoodOptions())` alongside existing model attributes.

---

#### 4. Enable and fix the four disabled ownership tests

**File**: `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryOwnershipTests.java`

**Intent**: Activate the pre-written ownership contract. Two tests need `moodTag` param added — without it Spring returns 400 before the ownership check fires.

**Contract**: Remove `@Disabled` from all four methods. Add `.param("moodTag", "CALM")` to both PATCH methods (`patchJournalEntryMoodReturns404...` and `patchJournalEntryMoodSucceeds...`). No other test logic changes.

---

#### 5. New PUT ownership tests

**File**: `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryOwnershipTests.java`

**Intent**: Extend ownership coverage to the content-edit endpoint that the original disabled tests did not scaffold.

**Contract**: Add two test methods:
- `putJournalEntryReturns404WhenIdBelongsToADifferentAuthenticatedUser` — `perform(put("/journal/{id}", entryId).param("content", "Nowa treść").with(user(USER_A_EMAIL)...).with(csrf())).andExpect(status().isNotFound())`.
- `putJournalEntrySucceedsWhenCalledByTheEntryOwner` — same but `USER_B_EMAIL`; expects `status().is3xxRedirection()`. Both include `@DisplayName`.

---

#### 6. Route protection tests for new routes

**File**: `src/test/java/com/amadeuszx/moodlog/security/SecurityRouteProtectionTests.java`

**Intent**: Confirm that all four new routes are covered by the `anyRequest().authenticated()` fallback without requiring explicit entries in `SecurityConfiguration`.

**Contract**: Add four anonymous-request tests (each follows the existing pattern: no auth, expect 302 redirect to login):
- `GET /journal/00000000-0000-0000-0000-000000000000/edit`
- `DELETE /journal/00000000-0000-0000-0000-000000000000`
- `PATCH /journal/00000000-0000-0000-0000-000000000000/mood`
- `PUT /journal/00000000-0000-0000-0000-000000000000`

Use a nil UUID — no entry needs to exist; the security check fires before route processing. Supply `.with(csrf())` on the `DELETE`, `PATCH`, and `PUT` requests so they reach the authentication entry point instead of failing at the CSRF filter.

### Success Criteria

#### Automated Verification

- `.\mvnw.cmd test` passes with all four previously disabled ownership tests now active.
- Two new PUT ownership tests pass.
- Four new route protection tests pass.

#### Manual Verification

- `DELETE /journal/{real-id}` with the logged-in owner returns 302 → `/journal/history` (verify via browser dev tools or curl).
- `DELETE /journal/{real-id}` as a different logged-in user (or with a fake ID) returns 404.
- `GET /journal/{real-id}/edit` renders without error for the owner.

**Pause here for manual confirmation before Phase 3.**

---

## Phase 3: Presentation Layer

### Overview

Add `UUID id` and the effective `MoodTag` to `JournalHistoryItem`, update `toHistoryItem` to populate both, create the `journal-edit.html` template, and update `journal-history.html` with per-entry delete / edit / mood-correction controls. Add E2E MockMvc flow tests.

### Changes Required

#### 1. JournalHistoryItem — add id and effective mood tag

**File**: `src/main/java/com/amadeuszx/moodlog/journal/history/JournalHistoryItem.java`

**Intent**: Surface entry identity and the current effective mood to the history template so delete/edit/mood-correction controls can target the right entry and pre-select the current tag safely.

**Contract**: Add `UUID id` as the first record component and `MoodTag effectiveMoodTag` before the display label: `JournalHistoryItem(UUID id, LocalDate displayDate, LocalTime displayTime, String excerpt, MoodTag effectiveMoodTag, String moodLabel, Integer moodScore)`.

---

#### 2. JournalEntryService — pass id and effective mood tag in toHistoryItem

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java`

**Intent**: Populate the new `id` and `effectiveMoodTag` fields in every `JournalHistoryItem` produced by `toHistoryItem`.

**Contract**: In `toHistoryItem(JournalEntry entry, ZoneId userZone)`, pass `entry.getId()` as the first argument and `effectiveMood.moodTag()` before the Polish label in the `JournalHistoryItem` constructor call. Update the existing constructor call accordingly; no other history mapping logic changes.

---

#### 3. New journal-edit.html template

**File**: `src/main/resources/templates/journal-edit.html`

**Intent**: Dedicated page for editing entry content, consistent with existing page structure.

**Contract**: Follow the navigation and layout conventions of `journal.html`. The form action is `/journal/{id}` where `{id}` comes from the `entryId` model attribute; method is POST with a `_method=PUT` hidden input. Bind the textarea to `journalEntryForm.content` with `th:field`. Include the CSRF token, `maxlength` tied to `journalMaxContentLength`, field-error display, a "Zapisz" submit button, and a "Anuluj" link back to `/journal/history`. Preserve Polish copy and UTF-8.

---

#### 4. journal-history.html — per-entry controls

**File**: `src/main/resources/templates/journal-history.html`

**Intent**: Let users delete, edit, or correct mood for each history entry without leaving the history page (except for the edit flow which navigates to the edit form).

**Contract**: Inside the `th:each="entry : ${historyEntries}"` loop, add three controls per entry:

- **Edit link**: `<a th:href="@{/journal/{id}/edit(id=${entry.id()})}">Edytuj</a>`.

- **Delete form**: POST to `/journal/{id}` with `_method=DELETE`, CSRF token, and `onsubmit="return confirm('Na pewno usunąć ten wpis?')"` on the `<form>` element. One submit button labeled "Usuń".

- **Mood correction form**: POST to `/journal/{id}/mood` with `_method=PATCH`, CSRF token, a `<select name="moodTag">` populated with `th:each="option : ${selectableMoodOptions}"` where `th:value="${option.tag()}"`, `th:text="${option.polishLabel()}"`, and `th:selected="${option.tag() == entry.effectiveMoodTag()}"`, plus a submit button labeled "Popraw nastrój".

---

#### 5. E2E flow tests

**File**: `src/test/java/com/amadeuszx/moodlog/journal/JournalFlowTests.java`

**Intent**: Verify the three new user flows end-to-end through the controller, service, and templates using MockMvc.

**Contract**: Add test methods (camelCase, `@DisplayName`, `val` for locals):

- **Delete flow**: create a user + entry; `DELETE /journal/{id}` as owner with `.with(csrf())` → expect 3xx; then `GET /journal/history` → expect the deleted entry's excerpt is absent from the response body.

- **Mood correction flow**: create a user + entry with system mood CALM; `PATCH /journal/{id}/mood` with `moodTag=SADNESS` and `.with(csrf())` as owner → expect 3xx; then `GET /journal/history` → expect Polish label for SADNESS appears, CALM label for that entry is absent.

- **Content edit flow**: create a user + entry; mock classifier to return JOY/85 on second call; `GET /journal/{id}/edit` → expect 200 + original content in response; `PUT /journal/{id}` with new content and `.with(csrf())` → expect 3xx; then `GET /journal/history` → expect new excerpt present.

- **Edit validation**: `PUT /journal/{id}` with blank content and `.with(csrf())` → expect 200 (re-render `journal-edit`), no update persisted.

- **Multi-user delete isolation**: user B owns entry; user A tries `DELETE /journal/{B-entry-id}` with `.with(csrf())` → 404; entry still visible to user B.

### Success Criteria

#### Automated Verification

- `.\mvnw.cmd test` passes including all new E2E flow tests.
- `JournalHistoryItem` has `UUID id` as the first component plus `effectiveMoodTag`, and `toHistoryItem` compiles and populates both.

#### Manual Verification

- Start app locally (`.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local`) with the local profile pinned to stub classification. Create a journal entry. Navigate to `/journal/history`. Confirm delete button appears and JS confirm fires. Confirm delete removes the entry. Confirm "Edytuj" link navigates to the edit form. Submit an edit with content that should map to a different stub keyword; confirm history shows the updated excerpt and re-classified mood. Confirm mood-correction dropdown shows Polish labels (no "UNKNOWN"). Select a correction; confirm history shows the corrected label.
- Verify no user can delete or edit another user's entry (test with two browser sessions or separate accounts).

**Pause here for manual confirmation before closing the change.**

---

## Testing Strategy

### Unit Tests

- `JournalEntryServiceTests`: all new service methods + `resolveEffectiveMood` behaviour with tag-only override.
- Existing DST tests (`JournalEntryServiceDstTests`) must still pass unmodified — the effective mood fix must not break trend bucketing.

### Integration Tests

- `JournalEntryOwnershipTests` (Phase 2): all 6 tests (4 enabled + 2 new PUT) confirm 404/3xx boundary.
- `SecurityRouteProtectionTests` (Phase 2): 4 new anonymous-redirect tests.
- `JournalFlowTests` (Phase 3): delete, edit, mood-correction, edit-validation, multi-user isolation.

### Manual Testing Steps

1. Start app on the stub-pinned local profile; register two accounts.
2. Log in as account A; create 3 entries.
3. Log in as account B; confirm B's history is empty.
4. As A: delete the oldest entry; confirm it disappears.
5. As A: edit the middle entry with substantially different text that should map to a different stub keyword; confirm the mood label changes.
6. As A: correct mood on remaining entry; confirm the Polish label updates to the selected tag.
7. As B: attempt to directly `DELETE` A's entry ID via the browser; confirm 404.

## References

- Research: `context/changes/entry-ownership-and-mood-correction/research.md`
- Ownership tests: `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryOwnershipTests.java`
- Service: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:422` — `resolveEffectiveMood` to fix
- Entity: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntry.java:68` — domain constructor shape
- S-02 archive: `context/archive/2026-05-30-first-mood-classified-entry/` — classification fallback pattern

---

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Domain Layer

#### Automated

- [x] 1.1 `.\mvnw.cmd test` passes (all existing + new service tests green) — 777c657
- [x] 1.2 `JournalEntryNotFoundException` carries `@ResponseStatus(NOT_FOUND)` — 777c657
- [x] 1.3 `findByIdAndUserAccountId` exists in `JournalEntryRepository` — 777c657
- [x] 1.4 `selectableMoodOptions()` returns 7 options (all `MoodTag` values minus `UNKNOWN`) — 777c657

#### Manual

- [x] 1.5 App starts on local profile; existing journal flows work unchanged — 777c657

### Phase 2: Web Layer

#### Automated

- [x] 2.1 `.\mvnw.cmd test` passes with all four previously disabled ownership tests now active — 9b201d4
- [x] 2.2 Two new PUT ownership tests pass — 9b201d4
- [x] 2.3 Four new route protection tests pass — 9b201d4

#### Manual

- [x] 2.4 `DELETE /journal/{real-id}` as owner returns 302 to `/journal/history` — 9b201d4
- [x] 2.5 `DELETE /journal/{real-id}` as non-owner returns 404 — 9b201d4
- [x] 2.6 `GET /journal/{real-id}/edit` renders without error for the owner — 9b201d4

### Phase 3: Presentation Layer

#### Automated

- [x] 3.1 `.\mvnw.cmd test` passes including all new E2E flow tests
- [x] 3.2 `JournalHistoryItem` has `UUID id` as first component plus `effectiveMoodTag`, and `toHistoryItem` compiles and populates both

#### Manual

- [x] 3.3 Delete button appears in history; JS confirm fires; entry disappears after delete
- [x] 3.4 "Edytuj" link navigates to edit form; submitting updates excerpt and mood in history
- [x] 3.5 Mood-correction dropdown shows Polish labels with no "UNKNOWN" option; selecting corrects the label
- [x] 3.6 Cross-user delete of another user's entry returns 404

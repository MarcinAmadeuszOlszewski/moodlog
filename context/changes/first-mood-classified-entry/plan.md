# First mood-classified journal entry Implementation Plan

## Overview

Implement the first real private-journal slice: an authenticated user can write and save free-text journal entries, receive an automatically classified mood tag plus score, and see a simple recent-entry list on `/journal`. This change also introduces the first production AI integration path behind a provider-neutral seam while keeping local/test behavior deterministic with a stub classifier.

## Current State Analysis

`/journal` is currently a protected shell with no persistence or submit flow: the controller only serves a placeholder CTA and hint, and the template renders an empty-state card instead of a form or saved entries (`src/main/java/com/amadeuszx/moodlog/JournalController.java:11-19`, `src/main/resources/templates/journal.html:13-18`). The surrounding auth boundary is already in place, including redirect-to-login behavior and saved-request return to `/journal`, so journaling can stay inside the existing private area without new security mechanics (`src/main/java/com/amadeuszx/moodlog/SecurityConfiguration.java:37-70`, `src/test/java/com/amadeuszx/moodlog/AuthenticationFlowTests.java:74-93`).

Persistence currently follows a simple additive Flyway + JPA pattern centered on `UserAccount`, with repository methods kept small and integration coverage favored over isolated controller tests (`src/main/resources/db/migration/V1__create_user_accounts.sql:1-9`, `src/main/java/com/amadeuszx/moodlog/UserAccount.java:11-30`, `src/main/java/com/amadeuszx/moodlog/UserAccountRepository.java:8-13`, `src/test/java/com/amadeuszx/moodlog/ApplicationTests.java:22-90`). The repo has no journal-entry model, no AI dependencies, and no provider configuration yet, but it already relies on property-driven configuration and a separate test-only properties file that overrides main settings during test boot (`pom.xml:32-114`, `src/main/resources/application.properties:1-16`, `src/test/resources/application.properties:1-6`).

This planning session intentionally makes one product decision explicit: S-02 will treat successful classification as a prerequisite for persistence. That means the first implementation will preserve the draft in the form on classifier failure instead of saving an `unknown` entry, even though earlier product notes assumed a save-first fallback.

## Desired End State

An authenticated user can submit multiple journal entries from `/journal`, see a newest-first list of the latest saved entries on the same page, and view a mood tag plus integer score (`0-100`) for each saved item. The classification result is stored in an app-owned contract, not in provider-native raw labels, and the persisted schema keeps system-assigned mood data separate from future user overrides so FR-006 can add manual correction cleanly.

Production uses a hosted AI provider through a `MoodClassifier` port, with OpenAI as the default adapter path and a deterministic stub implementation for local development and tests. A successful classification ends with a normal PRG redirect back to `/journal`; a classification failure returns the journal page with the draft preserved, a blocking error message, and no new entry inserted.

### Key Discoveries:

- `/journal` already sits behind the authenticated area and uses the current principal email as the user identifier, so ownership can reuse the existing auth boundary (`src/main/java/com/amadeuszx/moodlog/JournalController.java:12-17`).
- The app already has a Spring MVC validation + re-render-on-error pattern in the registration flow, which is the right baseline for journal form handling (`src/main/java/com/amadeuszx/moodlog/AuthController.java:69-98`, `src/main/java/com/amadeuszx/moodlog/RegistrationForm.java:9-28`).
- Repository and service tests already exist for the auth domain, so the new journal slice should mirror that split instead of inventing a different testing shape (`src/test/java/com/amadeuszx/moodlog/UserAccountRepositoryTests.java:29-75`, `src/test/java/com/amadeuszx/moodlog/UserAccountServiceTests.java:33-60`).
- Integration-heavy `MockMvc` coverage is the repo's main confidence layer for user-facing behavior, especially around auth and redirects (`src/test/java/com/amadeuszx/moodlog/ApplicationTests.java:45-90`, `src/test/java/com/amadeuszx/moodlog/AuthenticationFlowTests.java:94-252`).
- The current build has no AI starter or provider SDK, so the classifier seam, adapter, and configuration surface all need to be introduced from scratch (`pom.xml:32-114`).

## What We're NOT Doing

- Trend calculations, weekly summaries, or dedicated history analytics from S-03
- Manual correction UI, edit/delete flows, or any FR-006 screen work
- Background workers, queues, polling, or a persisted `PENDING` classification state
- Provider-native raw JSON storage or provider-specific labels shown directly in the UI
- Anonymous journal access, sharing, export, or cross-account visibility

## Implementation Approach

Build the journal feature as a normal vertical Spring Boot slice: Flyway migration first, then JPA entity/repository, then a journal application service that coordinates user lookup, AI classification, and persistence. Keep the classification capability behind a small app-owned `MoodClassifier` port so the journal controller and template stay provider-agnostic. Reuse the repo's existing form validation and post/redirect/get patterns: success redirects back to `/journal` with the new recent-entry list; classifier failures re-render the same view with the draft preserved and a user-safe error message.

## Critical Implementation Details

### State sequencing

This plan intentionally makes classification a hard prerequisite for persistence. The orchestration service should obtain and validate the classification result before creating a `JournalEntry` row; do not introduce a saved `unknown` or `PENDING` state in this slice unless the product decision changes.

### Debug & observability

`src/test/resources/application.properties` overrides main properties during test boot, so every new `moodlog.ai.*` or journal validation property needs a matching test default. Classification logs should record only safe user identifiers plus provider/model and failure reason categories; never log raw journal text.

## Phase 1: Journal entry domain and persistence

### Overview

Establish the first persisted journal-entry model, including ownership, recent-entry retrieval, and explicit separation between system-assigned classification and future user overrides.

### Changes Required:

#### 1. Journal entry schema

**File**: `src/main/resources/db/migration/V2__create_journal_entries.sql`

**Intent**: Add the first journal-entry table as an additive migration that fits the existing Flyway/JPA model and supports authenticated per-user entry storage.

**Contract**: Create a `journal_entries` table keyed by UUID with a foreign key to `user_accounts`, a bounded free-text `content` column aligned to the form limit, `system_mood_tag`, `system_mood_score`, nullable `override_mood_tag`, `override_mood_score`, classifier provider/model metadata, `classified_at`, and standard `created_at` / `updated_at` timestamps. Include an index that supports newest-first reads scoped to one user.

#### 2. Journal entry domain model

**File**: `src/main/java/com/amadeuszx/moodlog/JournalEntry.java`

**Intent**: Represent persisted journal entries in the same explicit-typed JPA style already used for `UserAccount`.

**Contract**: Map the entity to `journal_entries`, keep the system classification fields separate from override fields, and relate each entry to its owning `UserAccount` without introducing anonymous ownership paths.

#### 3. Mood contract and repository

**File**: `src/main/java/com/amadeuszx/moodlog/MoodTag.java`

**Intent**: Define the app-owned mood taxonomy that all providers must map into before persistence.

**Contract**: Introduce a fixed enum with seven tags (`JOY`, `CALM`, `NEUTRAL`, `SADNESS`, `ANXIETY`, `ANGER`, `OVERWHELMED`) used across persistence, service, and UI layers. Store scores as integers `0-100`, treat them as mood-intensity values, and keep provider-specific labels out of the domain model.

**File**: `src/main/java/com/amadeuszx/moodlog/JournalEntryRepository.java`

**Intent**: Add the repository queries needed for creation-time reads and the simple recent-entry list on `/journal`.

**Contract**: Provide a user-scoped newest-first query for the latest 10 entries belonging to one account and keep repository methods aligned with the current `JpaRepository` pattern used by `UserAccountRepository`.

#### 4. Persistence test coverage

**File**: `src/test/java/com/amadeuszx/moodlog/JournalEntryRepositoryTests.java`

**Intent**: Prove the migration, ownership boundary, and recent-entry ordering before the service and controller layers are added.

**Contract**: Cover successful persistence, newest-first ordering, and the guarantee that one user's entries never appear in another user's query results.

### Success Criteria:

#### Automated Verification:

- Flyway boots the journal entry schema under the test configuration: `.\mvnw.cmd -q "-Dtest=ApplicationTests,JournalEntryRepositoryTests" test`
- Repository tests prove newest-first ordering and user scoping: `.\mvnw.cmd -q "-Dtest=JournalEntryRepositoryTests" test`

#### Manual Verification:

- The application still boots with `/journal` behind authentication after the new migration is added
- Multiple entries can exist for one account conceptually without requiring any history UI beyond the recent list

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 2: Classification seam and application service

### Overview

Introduce the provider-neutral classification contract, wire the hosted-provider and stub implementations, and centralize the blocking-save orchestration in one journal service.

### Changes Required:

#### 1. Provider-neutral classification contract

**File**: `src/main/java/com/amadeuszx/moodlog/MoodClassifier.java`

**Intent**: Isolate the classification capability behind a small application-owned port so the journal flow does not depend directly on one provider SDK.

**Contract**: Define a method that accepts entry text and returns a validated app-owned mood tag, integer score, and provider/model metadata, or throws a domain-specific classification failure exception.

**File**: `src/main/java/com/amadeuszx/moodlog/MoodClassification.java`

**Intent**: Hold the normalized classification payload that the service persists on successful writes.

**Contract**: Represent `MoodTag`, mood-intensity score `0-100`, provider name, model name, and classification timestamp in a typed value object used by both real and stub adapters.

#### 2. Hosted provider and stub adapters

**File**: `pom.xml`

**Intent**: Add the minimum dependencies needed to call the chosen hosted model through Spring AI while keeping the app aligned with the existing Spring Boot stack.

**Contract**: Introduce Spring AI plus the default OpenAI integration path without removing the ability to run the application in stub mode.

**File**: `src/main/resources/application.properties`

**Intent**: Add property-driven AI and journal configuration that matches the repo's existing environment-variable pattern.

**Contract**: Define `moodlog.ai.*` properties for enablement, provider selection, default model, timeout, and recent-list limit, plus a journal text-length limit, with production-safe defaults that do not hardcode secrets.

**File**: `src/test/resources/application.properties`

**Intent**: Keep the test context bootable without hosted credentials.

**Contract**: Supply matching `moodlog.ai.*` and journal-validation defaults that force stub behavior in tests and preserve the repo's existing H2 test boot pattern.

**File**: `src/main/java/com/amadeuszx/moodlog/StubMoodClassifier.java`

**Intent**: Provide a deterministic classifier for tests and local development.

**Contract**: Return stable `MoodClassification` values from input text without reaching any external service, so MVC and service tests stay repeatable.

**File**: `src/main/java/com/amadeuszx/moodlog/OpenAiMoodClassifier.java`

**Intent**: Implement the default hosted-provider path selected during research and planning.

**Contract**: Use structured output to map provider responses into the app-owned mood enum + score contract and reject invalid or out-of-range results before they reach persistence.

#### 3. Journal application service

**File**: `src/main/java/com/amadeuszx/moodlog/JournalEntryService.java`

**Intent**: Own the journal use cases so the controller only coordinates HTTP and view concerns.

**Contract**: Provide a write method that resolves the current `UserAccount`, classifies entry text before save, persists the entry only on successful classification, exposes a recent-entry read method for `/journal`, and throws a user-safe exception when classification blocks the save.

#### 4. Service-level verification

**File**: `src/test/java/com/amadeuszx/moodlog/JournalEntryServiceTests.java`

**Intent**: Lock down the orchestration logic before the controller and template layers depend on it.

**Contract**: Cover successful classification-and-save, provider failure that blocks persistence, invalid score/tag rejection, and recent-entry reads via stubbed classifier output.

### Success Criteria:

#### Automated Verification:

- Service tests cover successful writes, blocking failures, and invalid classification payloads: `.\mvnw.cmd -q "-Dtest=JournalEntryServiceTests" test`
- The application context boots with stub classifier defaults and no hosted credentials: `.\mvnw.cmd -q "-Dtest=ApplicationTests" test`

#### Manual Verification:

- The application can boot locally with stub classification enabled and no provider secret configured
- Switching to hosted-provider mode requires only configuration changes, not controller or template edits

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 3: Journal write flow and recent-entry UI

### Overview

Replace the journal placeholder with the authenticated create flow, the simple recent-entry list, and the blocking failure UX chosen for this slice.

### Changes Required:

#### 1. Journal form and controller flow

**File**: `src/main/java/com/amadeuszx/moodlog/JournalEntryForm.java`

**Intent**: Introduce a dedicated form object for journal validation instead of overloading raw request parameters in the controller.

**Contract**: Validate non-blank content and the agreed maximum length, and keep the form reusable for both initial render and error re-render paths.

**File**: `src/main/java/com/amadeuszx/moodlog/JournalController.java`

**Intent**: Replace the GET-only placeholder controller with a real authenticated read/write flow.

**Contract**: Keep `GET /journal` for rendering the page, add `POST /journal` for entry submission, redirect back to `/journal` after a successful save, and return the `journal` view with preserved form data plus a blocking error on classification failure.

#### 2. Journal page template

**File**: `src/main/resources/templates/journal.html`

**Intent**: Turn the current empty-state shell into the first usable private-journal screen while preserving the existing Polish copy style and logout/navigation elements.

**Contract**: Render the entry form, validation and classification failure messages, an empty state when no entries exist, and a newest-first recent-entry list showing a content excerpt plus the Polish mood label and score for the signed-in user only.

#### 3. MVC integration coverage

**File**: `src/test/java/com/amadeuszx/moodlog/JournalFlowTests.java`

**Intent**: Add an integration-first test suite for the new journal behavior without weakening the existing auth-flow tests.

**Contract**: Cover authenticated entry creation, redirect-on-success, recent-list rendering, preserved text on classification failure, validation errors, and per-user isolation.

**File**: `src/test/java/com/amadeuszx/moodlog/AuthenticationFlowTests.java`

**Intent**: Update existing auth expectations so they reflect the new journal page instead of the placeholder shell.

**Contract**: Keep login/register coverage intact while switching `/journal` assertions from the temporary CTA copy to the real journal experience.

### Success Criteria:

#### Automated Verification:

- MVC integration tests cover success, validation failure, classification failure, and recent-entry rendering: `.\mvnw.cmd -q "-Dtest=JournalFlowTests,AuthenticationFlowTests" test`
- Anonymous journal requests still redirect to login while authenticated requests render the new journal page: `.\mvnw.cmd -q "-Dtest=ApplicationTests,JournalFlowTests" test`

#### Manual Verification:

- A logged-in user can submit several entries and sees them newest-first in the recent list on `/journal`
- A classification failure returns to the journal page with the draft preserved and no newly inserted entry
- One signed-in user never sees another user's entries in the UI

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 4: Operational hardening and regression coverage

### Overview

Add the safe operational polish needed for a hosted-provider MVP: privacy-safe logging, end-to-end regression coverage, and configuration verification across stub and hosted modes.

### Changes Required:

#### 1. Safe journal classification logging

**File**: `src/main/java/com/amadeuszx/moodlog/JournalEntryService.java`

**Intent**: Add operational visibility around journal classification without repeating the auth review mistake of leaking sensitive data.

**Contract**: Log success and failure events using a safe user identifier plus provider/model and reason codes only; do not log raw journal text or provider raw output.

**File**: `src/main/java/com/amadeuszx/moodlog/OpenAiMoodClassifier.java`

**Intent**: Surface provider-side failures in a way the service can turn into blocking, user-safe UI errors.

**Contract**: Translate provider and validation failures into domain exceptions with stable reason categories suitable for tests and logs.

#### 2. Regression and configuration verification

**File**: `src/test/java/com/amadeuszx/moodlog/ApplicationTests.java`

**Intent**: Keep the existing application-level smoke checks aligned with the new journal reality.

**Contract**: Preserve coverage for public routes and authenticated journal protection while adding confidence that the journal slice boots cleanly under test configuration.

**File**: `src/test/resources/application.properties`

**Intent**: Finalize the stub-mode defaults needed for a stable CI and test setup.

**Contract**: Ensure the full test suite runs without hosted credentials and with explicit defaults for any newly added journal or AI properties.

### Success Criteria:

#### Automated Verification:

- Full regression passes for the application and journal feature set: `.\mvnw.cmd test`
- Logging and failure-path tests prove that journal text is never emitted to logs: `.\mvnw.cmd -q "-Dtest=JournalEntryServiceTests,JournalFlowTests" test`

#### Manual Verification:

- Running the app in stub mode allows end-to-end journaling without external credentials
- Running the app with hosted-provider settings shows real classification without exposing raw entry text in logs

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

## Testing Strategy

### Unit Tests:

- Validate provider-output normalization, including invalid enum values and out-of-range scores
- Verify blocking classification failures do not call the repository save path
- Verify safe logging never includes raw journal text or provider raw payloads

### Integration Tests:

- Authenticated user submits a valid entry and sees it in the recent list after redirect
- Invalid entry text re-renders the journal page with validation feedback and no insert
- Classifier failure re-renders the journal page with the draft preserved and no insert
- Two users with separate sessions see only their own recent entries
- Existing auth/login flows continue to land on the working journal page

### Manual Testing Steps:

1. Log in, submit several entries, and confirm the recent list is newest-first and private to the current user
2. Start the app in stub mode and verify journaling works without any hosted-provider credential
3. Trigger a classifier failure path and confirm the draft remains in the form while no entry is added
4. Start the app with hosted-provider settings and confirm a real mood tag + score appears after submission
5. Inspect logs to confirm only safe identifiers and provider metadata are emitted

## Performance Considerations

Classification latency is user-visible because save waits for a result, so the hosted-provider call needs a bounded timeout and the form should cap input size to limit token cost and round-trip time. Keep the recent-entry query fixed-size at the latest 10 entries to avoid turning S-02 into an unbounded history screen before S-03 adds dedicated browsing and trend views.

## Migration Notes

This change is additive: existing users remain valid, and the new `journal_entries` table starts empty. Adding explicit override columns now avoids a later schema rewrite when FR-006 introduces manual corrections; the main follow-up risk is that a future return to save-first fallback behavior would require a second migration to add persisted status or error fields.

## References

- Related research: `context/changes/first-mood-classified-entry/research.md`
- Current journal shell: `src/main/java/com/amadeuszx/moodlog/JournalController.java:11-19`
- Current journal template: `src/main/resources/templates/journal.html:13-18`
- Existing form and validation pattern: `src/main/java/com/amadeuszx/moodlog/AuthController.java:69-98`
- Existing auth redirect and journal protection: `src/main/java/com/amadeuszx/moodlog/SecurityConfiguration.java:37-70`
- Existing JPA repository pattern: `src/main/java/com/amadeuszx/moodlog/UserAccountRepository.java:8-13`
- Existing integration test pattern: `src/test/java/com/amadeuszx/moodlog/AuthenticationFlowTests.java:74-252`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Journal entry domain and persistence

#### Automated

- [x] 1.1 Flyway boots the journal entry schema under the test configuration — c1a6538
- [x] 1.2 Repository tests prove newest-first ordering and user scoping — c1a6538

#### Manual

- [x] 1.3 The application still boots with `/journal` behind authentication after the new migration is added — c1a6538
- [x] 1.4 Multiple entries can exist for one account conceptually without requiring any history UI beyond the recent list — c1a6538

### Phase 2: Classification seam and application service

#### Automated

- [x] 2.1 Service tests cover successful writes, blocking failures, and invalid classification payloads — 604d6af
- [x] 2.2 The application context boots with stub classifier defaults and no hosted credentials — 604d6af

#### Manual

- [x] 2.3 The application can boot locally with stub classification enabled and no provider secret configured — 604d6af
- [x] 2.4 Switching to hosted-provider mode requires only configuration changes, not controller or template edits — 604d6af

### Phase 3: Journal write flow and recent-entry UI

#### Automated

- [x] 3.1 MVC integration tests cover success, validation failure, classification failure, and recent-entry rendering — 1eb8e28
- [x] 3.2 Anonymous journal requests still redirect to login while authenticated requests render the new journal page — 1eb8e28

#### Manual

- [x] 3.3 A logged-in user can submit several entries and sees them newest-first in the recent list on `/journal` — 1eb8e28
- [x] 3.4 A classification failure returns to the journal page with the draft preserved and no newly inserted entry — 1eb8e28
- [x] 3.5 One signed-in user never sees another user's entries in the UI — 1eb8e28

### Phase 4: Operational hardening and regression coverage

#### Automated

- [x] 4.1 Full regression passes for the application and journal feature set
- [x] 4.2 Logging and failure-path tests prove that journal text is never emitted to logs

#### Manual

- [x] 4.3 Running the app in stub mode allows end-to-end journaling without external credentials
- [x] 4.4 Running the app with hosted-provider settings shows real classification without exposing raw entry text in logs

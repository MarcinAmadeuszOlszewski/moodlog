---
date: 2026-06-15T00:00:00+02:00
researcher: m.olszewski
git_commit: e53748ee7da51db609169958c358df6a3b72f029
branch: master
repository: moodlog
topic: "Entry ownership and mood correction — S-04 (FR-004, FR-006)"
tags: [research, codebase, journal, ownership, mood-correction, delete, security]
status: complete
last_updated: 2026-06-15
last_updated_by: m.olszewski
---

# Research: Entry Ownership and Mood Correction (S-04)

**Date**: 2026-06-15  
**Researcher**: m.olszewski  
**Git Commit**: `e53748ee7da51db609169958c358df6a3b72f029`  
**Branch**: master  
**Repository**: MarcinAmadeuszOlszewski/moodlog

## Research Question

What currently exists in the codebase that S-04 must build on or extend to deliver FR-004 (edit or delete owned entries) and FR-006 (manually correct assigned mood tag)?

## Summary

The codebase is well-prepared for S-04. All required DB columns already exist (`override_mood_tag`, `override_mood_score`). Disabled tests in `JournalEntryOwnershipTests` already prescribe the exact HTTP surface: `DELETE /journal/{id}` and `PATCH /journal/{id}/mood`. The primary work is adding three service methods, one repository query, two controller endpoints, template changes to expose entry IDs, and enabling the four disabled ownership tests. No new Flyway migration is needed.

**Critical constraint:** The `JournalEntry` entity is currently immutable — no setters. Mutations (mood override, content edit) must use either entity-level update methods or `@Modifying @Query`. The plan must resolve this before implementation.

**Scope clarification:** Existing disabled tests cover only `DELETE` (not PUT/content-edit) and `PATCH /mood`. Content editing (the "edit" in FR-004) is not scaffolded yet. Plan must decide whether S-04 includes content editing or just delete + mood correction.

## Detailed Findings

### JournalEntry Entity

**File:** `src/main/java/com/amadeuszx/moodlog/journal/JournalEntry.java`

Fields set at construction (immutable after save):
- `id` (UUID, random) — primary key
- `userAccount` (ManyToOne LAZY to `UserAccount`, optional=false) — ownership FK
- `content` (String, max 2000)
- `systemMoodTag` (MoodTag enum, not null)
- `systemMoodScore` (Integer, nullable since V3)
- `overrideMoodTag` (MoodTag enum, **nullable** — column already exists)
- `overrideMoodScore` (Integer, **nullable** — column already exists)
- `classifierProvider`, `classifierModel`, `classifiedAt`
- `createdAt`, `updatedAt` (Instant)

**No setters on entity.** Lombok `@Getter` + `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + domain constructor only. S-04 mutations require either:
1. Adding targeted entity methods (`correctMood(MoodTag, Integer)`, `updateContent(String)`) that update fields + `updatedAt`, or
2. `@Modifying @Query` JPQL updates in the repository.

### Database Schema

All columns S-04 needs already exist (no migration required):

| Column | Type | Nullable | Purpose |
|---|---|---|---|
| `override_mood_tag` | VARCHAR(32) | YES | User-corrected mood tag |
| `override_mood_score` | INTEGER | YES | User-corrected score (0–100) |
| `updated_at` | TIMESTAMPTZ | NO | Set on every mutation |

Index on `(user_account_id, created_at)` supports ownership-scoped reads already.

**Reference migrations:**
- `src/main/resources/db/migration/V2__create_journal_entries.sql` — original schema
- `src/main/resources/db/migration/V3__nullable_system_mood_score.sql` — made system_mood_score nullable

### MoodTag Enum

**File:** `src/main/java/com/amadeuszx/moodlog/classification/MoodTag.java`

Values: `JOY`, `CALM`, `NEUTRAL`, `SADNESS`, `ANXIETY`, `ANGER`, `OVERWHELMED`, `UNKNOWN`

All are valid targets for a manual mood correction. `UNKNOWN` should likely be excluded from the user-selectable list (it's a fallback, not a meaningful user choice — confirm in planning).

### Effective Mood Rule

**File:** `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java` (lines ~422-436)

Private helper `resolveEffectiveMood(JournalEntry)` — if `overrideMoodTag` is set, use it; otherwise use `systemMoodTag`. Same rule applied in the projection version for trends. S-04's edit UI and any post-correction display must honor this rule.

### JournalEntryService — Existing Methods

**File:** `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java`

| Method | Description |
|---|---|
| `saveEntry(email, content)` | Create + classify new entry |
| `getRecentEntries(email)` | Top 10 newest entries for user |
| `getRecentEntryListItems(email)` | Maps recent entries → `JournalEntryListItem` |
| `getHistoryEntries(email, page)` | Paginated history → `Page<JournalHistoryItem>` |
| `getTrendView(email)` | Trend analytics view |

**Missing for S-04:**
- `deleteEntry(String userEmail, UUID entryId)` — ownership check, then delete
- `overrideMoodTag(String userEmail, UUID entryId, MoodTag tag, Integer score)` — ownership check, then update
- (If content edit is in scope) `updateContent(String userEmail, UUID entryId, String newContent)` — ownership check, then update content + updatedAt

### JournalEntryRepository — Existing Methods

**File:** `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryRepository.java`

Extends `JpaRepository<JournalEntry, UUID>`. Custom derived methods:
- `findTop10ByUserAccountIdOrderByCreatedAtDesc(UUID)`
- `findByUserAccountIdOrderByCreatedAtDesc(UUID, Pageable)`
- `findAllByUserAccountIdOrderByCreatedAtDesc(UUID, Pageable)` → `Page<JournalEntry>`
- Trend projection query (long derived name, returns `List<JournalTrendEntryProjection>`)

**Missing for S-04:**
- `findByIdAndUserAccountId(UUID id, UUID userAccountId)` — single-query ownership validation (returns `Optional<JournalEntry>`)

### JournalController — Existing Routes

**File:** `src/main/java/com/amadeuszx/moodlog/journal/JournalController.java`

| Route | Method | Description |
|---|---|---|
| `/journal` | GET | Render journal page with recent entries |
| `/journal` | POST | Create new entry (PRG on success) |
| `/journal/history` | GET | Paginated entry history |
| `/journal/trends` | GET | Trend analytics |

Auth resolved via `authentication.getName()` (user email) on every handler.

**Missing for S-04 (per disabled test expectations):**
- `DELETE /journal/{id}` — delete owned entry
- `PATCH /journal/{id}/mood` — correct mood tag

**No HiddenHttpMethodFilter in use.** All current forms are POST. To support DELETE/PATCH from Thymeleaf forms, either:
1. Enable `HiddenHttpMethodFilter` (`spring.mvc.hiddenmethod.filter.enabled=true`) and use `_method` hidden field in forms, or
2. Use plain POST routes (`POST /journal/{id}/delete`, `POST /journal/{id}/mood`) and map them as POST in controller.

The disabled tests use `DELETE` and `PATCH` methods — so HiddenHttpMethodFilter or testing with `MockMvc.perform(delete(...))` / `MockMvc.perform(patch(...))` must align.

### Templates — Missing for S-04

**Files:** `src/main/resources/templates/journal.html`, `src/main/resources/templates/journal-history.html`

Neither template exposes entry IDs. The `JournalEntryListItem` and `JournalHistoryItem` DTOs have no `id` field.

**Required:**
1. Add `id` (UUID) field to `JournalEntryListItem` and/or `JournalHistoryItem`
2. Add delete form (POST with `_method=DELETE`) per entry in `journal-history.html`
3. Add mood-correction form (POST with `_method=PATCH`) per entry in `journal-history.html`

CSRF token pattern already present in all templates (`th:name="${_csrf.parameterName}" th:value="${_csrf.token}"`).

### Security Configuration

**File:** `src/main/java/com/amadeuszx/moodlog/user/SecurityConfiguration.java` (lines 37-54)

`anyRequest().authenticated()` already protects all unmapped routes, including new `DELETE /journal/{id}` and `PATCH /journal/{id}/mood`. No changes to `SecurityConfiguration` needed unless you want explicit route declarations.

### Ownership Tests — Already Written, Disabled

**File:** `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryOwnershipTests.java`

Four tests exist but are all `@Disabled("Activate when S-04 edit/delete endpoints ship")`:

| Test | HTTP | Scenario | Expected |
|---|---|---|---|
| `deleteJournalEntryReturns404WhenIdBelongsToADifferentAuthenticatedUser` | `DELETE /journal/{id}` | Non-owner | 404 |
| `patchJournalEntryMoodReturns404WhenIdBelongsToADifferentAuthenticatedUser` | `PATCH /journal/{id}/mood` | Non-owner | 404 |
| `deleteJournalEntrySucceedsWhenCalledByTheEntryOwner` | `DELETE /journal/{id}` | Owner | 3xx |
| `patchJournalEntryMoodSucceedsWhenCalledByTheEntryOwner` | `PATCH /journal/{id}/mood` | Owner | 3xx |

**Response code for non-owner is 404, not 403** — avoids leaking entry existence to cross-user callers.

MockMvc setup in these tests:
```java
mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
    .apply(springSecurity())
    .build();
// per request:
.with(user(USER_A_EMAIL).roles("USER"))
.with(csrf())
```

### Test Patterns Already Established

From `JournalFlowTests`, `JournalEntryServiceTests`, `SecurityRouteProtectionTests`:
- `@SpringBootTest` + MockMvc with `.apply(springSecurity())` for all integration tests
- Test doubles: `@MockitoBean MoodClassifier`, `@MockitoBean Clock` (for DST tests)
- Fixture helpers: `createUserAccount(email)`, `createJournalEntry(...)`, `createJournalEntries(...)`
- Fixed clock: `@Import(FixedClockTestConfiguration.class)` — `Clock.fixed("2026-06-01T10:00:00Z")`
- H2 in MODE=PostgreSQL for tests; Flyway migrations run against both H2 and PostgreSQL
- `@BeforeEach` clears all repos for test isolation
- Privacy pattern: never log entry text; log email hash + provider + model only

**AGENTS.md test naming rules (must follow):**
- Test method names: camelCase, NO underscores
- `@DisplayName` required on every test method
- Local vars in tests: Lombok `val` (not `var` or explicit types)

## Code References

- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntry.java` — Entity, fields, immutability constraint
- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java` — Service, add delete/override methods here
- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryRepository.java` — Add `findByIdAndUserAccountId`
- `src/main/java/com/amadeuszx/moodlog/journal/JournalController.java` — Add DELETE/PATCH handlers
- `src/main/java/com/amadeuszx/moodlog/journal/history/JournalEntryListItem.java` — Add `id` field
- `src/main/java/com/amadeuszx/moodlog/journal/history/JournalHistoryItem.java` — Add `id` field
- `src/main/java/com/amadeuszx/moodlog/classification/MoodTag.java` — Mood enum values
- `src/main/resources/templates/journal-history.html` — Add delete/mood forms with entry IDs
- `src/main/resources/db/migration/V2__create_journal_entries.sql` — override columns already present
- `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryOwnershipTests.java` — Enable 4 disabled tests
- `src/test/java/com/amadeuszx/moodlog/journal/JournalFlowTests.java` — Extend with S-04 flows
- `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryServiceTests.java` — Add delete/override unit tests

## Architecture Insights

**Effective mood resolution must be preserved.** The `resolveEffectiveMood` helper in `JournalEntryService` already handles override-if-present logic. After a `PATCH /journal/{id}/mood`, the history and trends views will automatically show the corrected mood because they already call `resolveEffectiveMood`.

**Entity immutability is the key design tension.** The current entity has no setters. The idiomatic Spring Boot approach is to add narrow update methods on the entity (e.g., `correctMood(MoodTag, Integer)`), keeping the entity's business invariants in one place while enabling JPA dirty checking. This aligns better with the "explicit domain constructor only" pattern in lessons.md than using `@Modifying @Query`.

**Ownership validation at service layer.** All ownership checks belong in `JournalEntryService`, not the controller. The controller catches the resulting exception and returns 404. This mirrors the existing pattern where user resolution is service-owned.

**No new Flyway migration needed.** `override_mood_tag` and `override_mood_score` columns are already nullable in `journal_entries` (V2 schema). `updated_at` is already present. Zero schema changes for S-04.

**HiddenHttpMethodFilter decision is non-trivial.** The disabled ownership tests use `DELETE` and `PATCH` methods directly in MockMvc — but Thymeleaf forms only support GET/POST. Enabling `HiddenHttpMethodFilter` is the standard Spring MVC bridge. Alternatively, pure POST routes (`/journal/{id}/delete`, `/journal/{id}/mood`) avoid the filter but diverge from the test expectations. The plan must pick one and align templates + tests.

## Historical Context (from prior changes)

- `context/archive/2026-05-29-private-journal-access/` — Established Spring Security session auth, `UserAccount` model, email-as-username pattern. S-04 inherits `authentication.getName()` for identity resolution.
- `context/archive/2026-05-30-first-mood-classified-entry/` — Established `JournalEntry` entity with separate system/override mood columns (designed for S-04 from day one), `MoodTag` enum, `MoodClassification` DTO, classification-failure → UNKNOWN fallback.
- `context/archive/2026-06-01-history-and-mood-trends/` — Established `resolveEffectiveMood` rule, `open-in-view=false` DTO mandate, Europe/Warsaw timezone handling, package reorganization into feature sub-packages.
- Testing-ownership-security-enforcement (archived) — Added all four `@Disabled` tests in `JournalEntryOwnershipTests`, specifying the exact HTTP surface (DELETE/PATCH), expected codes (404 for non-owner, 3xx for owner), and auth patterns.

## Related Research

No other research artifacts exist yet for this change.

## Open Questions

1. **Is content editing in scope for S-04?** The disabled tests only cover `DELETE` and `PATCH /mood`. FR-004 says "edit or delete." The plan should confirm: delete-only + mood override, or delete + content edit + mood override?
2. **HiddenHttpMethodFilter or POST-only routes?** The test surface uses DELETE/PATCH. Template forms need either the filter (`spring.mvc.hiddenmethod.filter.enabled=true`) or a different route strategy. Must decide before implementation.
3. **UNKNOWN in mood-correction dropdown?** Should the user be able to manually set mood to `UNKNOWN`? It's a fallback tag, not a meaningful user-chosen label. Likely should be excluded.
4. **Re-classify on content edit?** If content editing is in scope and the user changes entry text, should mood classification re-run? This has cost and UX implications.
5. **Entity update mechanism?** Add narrow update methods to `JournalEntry` (preferred) vs `@Modifying @Query`? Must decide before implementing service methods.
6. **Override score: required or optional?** The `override_mood_score` column is nullable. Should mood correction require a score (0–100) or allow tag-only override with null score?

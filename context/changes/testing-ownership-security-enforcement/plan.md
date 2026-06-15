# Ownership and Security Enforcement Tests — Implementation Plan

## Overview

Plant two new test classes that make Risk #4 (IDOR on write endpoints) and Risk #5 (security config regression) observable in the test suite. Phase 1 adds a dedicated security fence that asserts every protected route blocks anonymous access. Phase 2 adds `@Disabled` ownership-contract stubs that prevent a naive `findById`-based S-04 implementation from passing test review. Phase 3 fills in the test-plan cookbook so future contributors know the patterns.

## Current State Analysis

Risk #5 is partially covered: `AuthenticationFlowTests` asserts anonymous redirect on all three journal GET routes, but those assertions are embedded inside multi-step saved-request/login-flow tests. No standalone security fence exists. Anonymous `POST /journal` and an arbitrary novel path are untested.

Risk #4 cannot be tested yet: `JournalController` has no PUT/PATCH/DELETE methods (S-04 is `proposed`, no change folder opened). A naïve S-04 implementation using the inherited `JpaRepository.findById(UUID)` (unscoped) would be vulnerable to IDOR — unlike all existing read queries which use scoped `ByUserAccountId` repository methods. Phase 3 stubs document this contract as a pre-condition so it cannot ship silently.

Test infrastructure is fully established: `@SpringBootTest` + `MockMvcBuilders.webAppContextSetup(...).apply(springSecurity()).build()` is the approved pattern for all security tests. No new Spring configuration or profiles are needed.

### Key Discoveries:

- `JournalController` (`:40,53,78,96`) — GET×3 + POST only; zero write endpoints
- `SecurityConfiguration` (`:37–53`) — explicit permit-list + `anyRequest().authenticated()` deny-all; journal routes are not in the permit list
- `JournalEntryRepository` (`:27–37`) — all custom queries scoped via `ByUserAccountId`; `findById` (unscoped) inherited from `JpaRepository` is the IDOR trap
- `AuthenticationFlowTests` (`:241,310,343`) — anonymous redirect assertions exist but are bundled inside multi-step login-flow tests, not readable as a standalone fence
- No test in `src/test/java/.../security/` package yet; package must be created

## Desired End State

A clean, standalone `SecurityRouteProtectionTests` class in a new `security` package that a reader immediately identifies as the security regression gate — separate from login-flow tests. A `JournalEntryOwnershipTests` class in the `journal` package with four `@Disabled` stubs: anyone activating S-04 removes the `@Disabled` annotations and the tests immediately verify the 404-via-`findByIdAndUserAccountId` contract. Cookbook sections §6.2, §6.4, and §6.6 in `test-plan.md` are filled in.

To verify: `.\mvnw.cmd test` passes with 5 new tests green (Phase 1) and 4 tests skipped/disabled (Phase 2). `test-plan.md` §6.2, §6.4, §6.6 are no longer "TBD".

## What We're NOT Doing

- Writing Phase 2 stubs against real DELETE/PATCH endpoints — they do not exist yet
- Testing login-and-redirect behavior (that belongs in `AuthenticationFlowTests`)
- Adding `@PreAuthorize` or method-security annotations — the codebase uses structural query scoping, not declarative security
- Adding `findByIdAndUserAccountId` to `JournalEntryRepository` — that belongs to the S-04 implementation change
- Removing or replacing existing anonymous-redirect tests in `AuthenticationFlowTests` — Phase 1 is intentionally redundant to form a dedicated readable fence

## Implementation Approach

Two new classes, one cookbook update:

1. `SecurityRouteProtectionTests` — `@SpringBootTest`, `springSecurity()`, five focused anonymous-request tests. Each test is a single `mockMvc.perform(...).andExpect(...)` — no session tracking, no login follow-through.
2. `JournalEntryOwnershipTests` — `@SpringBootTest`, `springSecurity()`, four `@Disabled` stubs. `@BeforeEach` seeds two users and one entry owned by user B; the test body performs the cross-user or same-user mutation and asserts the expected status. All tests are disabled until S-04 ships.
3. `test-plan.md` cookbook update — fill §6.2 (MockMvc security setup), §6.4 (ownership verification via scoped repo query), §6.6 (Phase 3 per-phase notes).

## Critical Implementation Details

**CSRF on anonymous POST test**: Spring Security's CSRF filter runs before the authentication filter. An anonymous `POST /journal` without a CSRF token returns 403 (CSRF rejection) — not 302 (auth redirect). The anonymous POST test in Phase 1 must include `.with(csrf())` to reach the authentication filter and get the expected redirect response.

---

## Phase 1: Security Route Protection Fence

### Overview

Create `SecurityRouteProtectionTests` in a new `security` test package. Five tests, each asserting a single anonymous request redirects to `/login`. Intentionally redundant with some existing `AuthenticationFlowTests` coverage — the purpose is a readable, standalone security regression gate.

### Changes Required:

#### 1. New test class

**File**: `src/test/java/com/amadeuszx/moodlog/security/SecurityRouteProtectionTests.java`

**Intent**: Establish a dedicated security fence asserting that every protected journal route rejects anonymous access with a redirect to `/login`. Isolated from login-flow tests so a reader immediately recognizes this as the security-config regression gate.

**Contract**: `@SpringBootTest` class in package `com.amadeuszx.moodlog.security`. `@BeforeEach` builds `mockMvc` via `MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build()`. No `@Autowired` repository fields needed — tests perform no DB writes. Five `@Test` methods:

1. Anonymous `GET /journal` → `status().is3xxRedirection()` + `redirectedUrl("/login")`
2. Anonymous `GET /journal/history` → `status().is3xxRedirection()` + `redirectedUrl("/login")`
3. Anonymous `GET /journal/trends` → `status().is3xxRedirection()` + `redirectedUrl("/login")`
4. Anonymous `POST /journal` with `.with(csrf())` → `status().is3xxRedirection()` + `redirectedUrl("/login")`
5. Anonymous `GET /journal/nonexistent-route` → `status().is3xxRedirection()` + `redirectedUrl("/login")`

Test 5 is the explicit deny-all catch-all: it asserts that a path not mapped to any controller is still protected, making the `anyRequest().authenticated()` contract observable.

### Success Criteria:

#### Automated Verification:

- `.\mvnw.cmd test` passes with 5 new tests green in `SecurityRouteProtectionTests`
- No regressions in `AuthenticationFlowTests`, `JournalFlowTests`, or `JournalTrendsFlowTests`

#### Manual Verification:

- All 5 test methods have `@DisplayName` values that read as plain-English security assertions (visible in IDE test runner and CI output)
- Test class is in `com.amadeuszx.moodlog.security` package, not `user` or `journal`

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 2: Ownership Contract Stubs

### Overview

Create `JournalEntryOwnershipTests` in the `journal` test package with four `@Disabled` stubs documenting the S-04 ownership contract. These tests compile and run when enabled but are disabled until S-04 ships write endpoints.

### Changes Required:

#### 1. New test class with disabled stubs

**File**: `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryOwnershipTests.java`

**Intent**: Document the ownership contract that S-04 must satisfy — cross-user mutations return 404 (via `findByIdAndUserAccountId` pattern), same-user mutations succeed. Activating the stubs (removing `@Disabled`) is the S-04 team's gate before shipping.

**Contract**: `@SpringBootTest` class in package `com.amadeuszx.moodlog.journal`. `@BeforeEach` calls `journalEntryRepository.deleteAll()` then `userAccountRepository.deleteAll()`, seeds two user accounts (`userA`, `userB`), and creates one `JournalEntry` owned by `userB`. Four `@Test` methods, each annotated `@Disabled("Activate when S-04 edit/delete endpoints ship")`:

1. `@DisplayName("DELETE /journal/{id} returns 404 when id belongs to a different authenticated user")` — `DELETE /journal/{id}` with `userB`'s entry UUID, authenticated as `userA` → `status().isNotFound()`
2. `@DisplayName("PATCH /journal/{id}/mood returns 404 when id belongs to a different authenticated user")` — `PATCH /journal/{id}/mood` with `userB`'s entry UUID, authenticated as `userA` → `status().isNotFound()`
3. `@DisplayName("DELETE /journal/{id} succeeds when called by the entry owner")` — same entry UUID, authenticated as `userB` → `status().is3xxRedirection()` (expected redirect after delete)
4. `@DisplayName("PATCH /journal/{id}/mood succeeds when called by the entry owner")` — same entry UUID, authenticated as `userB` → `status().is3xxRedirection()` (expected redirect after update)

All four use `.with(user(email).roles("USER"))` + `.with(csrf())`. `userB`'s entry UUID is stored in a `@BeforeEach`-scoped field.

### Success Criteria:

#### Automated Verification:

- `.\mvnw.cmd test` passes; the 4 stub tests appear as skipped/disabled (not failed) in the report
- No compile errors in `JournalEntryOwnershipTests` — all imports resolve, all referenced types exist

#### Manual Verification:

- Each `@Disabled` annotation carries the exact message `"Activate when S-04 edit/delete endpoints ship"`
- `@DisplayName` on each test reads as a plain-English ownership contract assertion
- The cross-user tests assert `isNotFound()` (not `isForbidden()`) — reflecting the 404-via-`findByIdAndUserAccountId` decision

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 3: Cookbook Documentation

### Overview

Fill in §6.2, §6.4, and §6.6 in `context/foundation/test-plan.md`. These sections have been "TBD — see Phase N" since the plan was written; Phase 3 delivers the patterns they reference.

### Changes Required:

#### 1. §6.2 — MockMvc + spring-security-test setup pattern

**File**: `context/foundation/test-plan.md`

**Intent**: Document the canonical `@SpringBootTest` + `springSecurity()` setup so future contributors don't have to reverse-engineer it from existing test classes.

**Contract**: Replace the `§6.2` TBD line. Content covers: `@SpringBootTest` annotation, `@Autowired WebApplicationContext`, `@BeforeEach` MockMvc build via `MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build()`, and the two request post-processors: `.with(user(email).roles("USER"))` for authenticated requests and `.with(csrf())` for state-mutating requests. Reference `SecurityRouteProtectionTests` and `AuthenticationFlowTests` as live examples.

#### 2. §6.4 — Ownership verification pattern

**File**: `context/foundation/test-plan.md`

**Intent**: Document `findByIdAndUserAccountId` as the mandatory scoped-query pattern for all write endpoints, and explain why `JpaRepository.findById` (unscoped) must not be used for ownership-checked operations.

**Contract**: Replace the `§6.4` TBD line. Content covers: (a) the IDOR trap — `JpaRepository.findById` is unscoped and will return any entry regardless of `userAccountId`; (b) the correct pattern — `findByIdAndUserAccountId(UUID entryId, UUID userAccountId)` returning `Optional.empty()` for cross-user access, which surfaces as 404; (c) activating `JournalEntryOwnershipTests` stubs is the verification step when S-04 ships. Reference `JournalEntryRepository` as the file to add the method to.

#### 3. §6.6 — Phase 3 per-rollout-phase notes

**File**: `context/foundation/test-plan.md`

**Intent**: Record what Phase 3 shipped and what the stubs are waiting on, so future readers understand the test suite's current state without re-reading the full plan.

**Contract**: Add a `#### Phase 3` entry under `§6.6`. Content: Phase 3 delivered `SecurityRouteProtectionTests` (5 tests, security regression fence) and `JournalEntryOwnershipTests` (4 disabled stubs, ownership contract anchor for S-04). Stubs activate when S-04 ships write endpoints — remove `@Disabled("Activate when S-04 edit/delete endpoints ship")` from all four methods.

### Success Criteria:

#### Automated Verification:

- `.\mvnw.cmd test` still passes (no test changes in this phase)
- §6.2, §6.4, §6.6 in `test-plan.md` contain no "TBD" placeholders

#### Manual Verification:

- §6.4 explicitly names `findByIdAndUserAccountId` as the mandatory pattern and explains why `findById` is the IDOR trap
- §6.6 entry names both new test classes and explains the stub activation step

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Testing Strategy

### Integration Tests:

- `SecurityRouteProtectionTests`: 5 anonymous-request tests against the live Spring Security filter chain
- `JournalEntryOwnershipTests`: 4 disabled stubs; activated by removing `@Disabled` when S-04 ships

### Manual Testing Steps:

1. Run `.\mvnw.cmd test` — confirm 5 new tests green, 4 disabled, no regressions
2. Inspect test report output — confirm disabled tests appear as skipped with the `@Disabled` message visible
3. Read `test-plan.md §6.2`, `§6.4`, `§6.6` — confirm no "TBD" remaining, patterns are actionable

## References

- Research: `context/changes/testing-ownership-security-enforcement/research.md`
- `SecurityConfiguration` permit list + deny-all: `src/main/java/com/amadeuszx/moodlog/user/SecurityConfiguration.java:37–53`
- Existing anonymous-redirect tests (bundled): `src/test/java/com/amadeuszx/moodlog/user/AuthenticationFlowTests.java:241,310,343`
- IDOR trap — unscoped inherited method: `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryRepository.java` (`findById` via `JpaRepository`)
- All scoped read queries (correct pattern to follow): `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryRepository.java:27–37`
- S-04 roadmap entry: `context/foundation/roadmap.md:91`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Security Route Protection Fence

#### Automated

- [x] 1.1 `.\mvnw.cmd test` passes with 5 new tests green in `SecurityRouteProtectionTests` — fcff6c0
- [x] 1.2 No regressions in `AuthenticationFlowTests`, `JournalFlowTests`, or `JournalTrendsFlowTests` — fcff6c0

#### Manual

- [x] 1.3 All 5 test methods have `@DisplayName` values that read as plain-English security assertions — fcff6c0
- [x] 1.4 Test class is in `com.amadeuszx.moodlog.security` package — fcff6c0

### Phase 2: Ownership Contract Stubs

#### Automated

- [x] 2.1 `.\mvnw.cmd test` passes; 4 stub tests appear as skipped/disabled (not failed) — a271788
- [x] 2.2 No compile errors in `JournalEntryOwnershipTests` — a271788

#### Manual

- [x] 2.3 Each `@Disabled` annotation carries the exact message `"Activate when S-04 edit/delete endpoints ship"` — a271788
- [x] 2.4 Cross-user tests assert `isNotFound()` (not `isForbidden()`) — a271788

### Phase 3: Cookbook Documentation

#### Automated

- [x] 3.1 `.\mvnw.cmd test` still passes
- [x] 3.2 §6.2, §6.4, §6.6 in `test-plan.md` contain no "TBD" placeholders

#### Manual

- [x] 3.3 §6.4 names `findByIdAndUserAccountId` and explains the `findById` IDOR trap
- [x] 3.4 §6.6 Phase 3 entry names both new test classes and the stub activation step

---
date: 2026-06-15T00:00:00Z
researcher: claude-sonnet-4-6
git_commit: 3d199d182c1fad6836508b96a85f29e86bbfd42f
branch: master
repository: moodlog
topic: "Phase 3 test rollout — ownership and security enforcement (Risks #4, #5)"
tags: [research, security, ownership, idor, spring-security, mockmvc, s-04]
status: complete
last_updated: 2026-06-15
last_updated_by: claude-sonnet-4-6
---

# Research: Phase 3 — Ownership and Security Enforcement

**Date**: 2026-06-15  
**Git Commit**: 3d199d182c1fad6836508b96a85f29e86bbfd42f  
**Branch**: master  
**Repository**: moodlog

## Research Question

Ground rollout Phase 3 of `context/foundation/test-plan.md`: "Ownership + security enforcement". Verify or correct risk response guidance for Risk #4 (S-04 IDOR on edit/delete/override) and Risk #5 (security config regression unprotecting journal routes). Identify existing coverage, genuine gaps, and the cheapest useful test layer for each.

---

## Summary

**Risk #4 — S-04 IDOR: Endpoints do not exist yet.**  
`JournalController` has no PUT/PATCH/DELETE methods. S-04 is roadmap status `proposed` with no implementation started. Meaningful ownership tests against mutation endpoints cannot be written yet. However, research identified a concrete IDOR trap: `JpaRepository.findById(UUID)` is unscoped and available; any S-04 implementation that uses it without additionally scoping by `userAccountId` will be vulnerable. Phase 3 should define the ownership contract as a failing stub and document the `findByIdAndUserAccountId` pattern so S-04 cannot ship a vulnerable naive implementation.

**Risk #5 — Security config regression: Already substantially covered.**  
All three protected GET routes (`/journal`, `/journal/history`, `/journal/trends`) already have explicit anonymous-redirect assertions in `AuthenticationFlowTests`. The `anyRequest().authenticated()` deny-all is in place and structurally catches all unmatched paths. Genuine gaps: (a) anonymous `POST /journal` is untested, (b) there is no standalone "deny-all catch-all" test for an arbitrary novel path, (c) existing coverage is bundled inside saved-request/login-flow tests rather than isolated security assertions.

---

## Detailed Findings

### Finding 1 — S-04 implementation status (Risk #4 blocker)

`JournalController` (`src/main/java/com/amadeuszx/moodlog/journal/JournalController.java`) exposes exactly four handler methods:

| Line | Method | Mapping |
|------|--------|---------|
| 40 | `journalPage` | `GET /journal` |
| 53 | `saveJournalEntry` | `POST /journal` |
| 78 | `historyPage` | `GET /journal/history` |
| 96 | `trendsPage` | `GET /journal/trends` |

No PUT, PATCH, or DELETE endpoints exist. Roadmap S-04 (`context/foundation/roadmap.md:91`) is `proposed`. PRD FR-004 ("authenticated user can edit or delete their own entries") and FR-006 ("authenticated user can manually correct the assigned mood tag") are marked `must-have` but unimplemented.

**Impact on Risk #4 test guidance**: The test plan says "prove PUT/PATCH/DELETE on an entry ID belonging to user B returns 403 or 404 when user A is authenticated." These endpoints do not exist. An authenticated MockMvc request to `DELETE /journal/{id}` currently returns 404 for *all* users — there is no ownership signal in that response. Tests written now against fictional endpoints provide no meaningful signal.

**Recommended correction to test plan guidance**: Risk #4 tests must be split into two phases:
- Phase 3 (now): document the ownership contract and plant a `@Disabled` stub to prevent a vulnerable naive S-04 implementation from slipping through.
- S-04 implementation change (future): activate and extend the stub tests against real endpoints.

### Finding 2 — The IDOR trap: `findById` vs. scoped repo queries

All current `JournalEntryRepository` queries are scoped by `userAccountId`:

```java
// JournalEntryRepository.java:27–37
List<JournalEntry> findTop10ByUserAccountIdOrderByCreatedAtDesc(UUID userAccountId);
List<JournalEntry> findByUserAccountIdOrderByCreatedAtDesc(UUID userAccountId, Pageable pageable);
Page<JournalEntry> findAllByUserAccountIdOrderByCreatedAtDesc(UUID userAccountId, Pageable pageable);
List<JournalTrendEntryProjection> findTrendEntriesByUserAccountIdAnd...(UUID userAccountId, ...);
```

`JournalEntryRepository` also inherits `JpaRepository<JournalEntry, UUID>`, which includes:
```java
Optional<JournalEntry> findById(UUID id);  // UNSCOPED — fetches any entry regardless of owner
```

`JournalEntry` links to its owner via:
```java
// JournalEntry.java:32–34
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_account_id", nullable = false)
private UserAccount userAccount;
```

A naive S-04 implementation calling `findById(pathVariableId)` before checking ownership would expose any entry to any authenticated user. The correct pattern is either:
- `findByIdAndUserAccountId(UUID entryId, UUID userAccountId)` — returns `Optional.empty()` for cross-user access, surfaces as 404
- Load via `findById` then assert `entry.getUserAccount().getId().equals(currentUser.getId())` and throw 403 explicitly

**The safer pattern for S-04** is `findByIdAndUserAccountId` because it returns `Optional.empty()` without loading the foreign entry — no information leak even in the presence of timing side-channels.

### Finding 3 — Current ownership enforcement for reads (structurally sound)

All service methods that produce user-visible data call `resolveUserAccount` and pass its UUID to scoped repository methods:

```java
// JournalEntryService.java:74, 148, 161, 171
final UserAccount userAccount = resolveUserAccount(currentUserEmail);
// then:
journalEntryRepository.findTop10ByUserAccountId(userAccount.getId(), ...);
journalEntryRepository.findByUserAccountIdOrderByCreatedAtDesc(userAccount.getId(), ...);
journalEntryRepository.findAllByUserAccountIdOrderByCreatedAtDesc(userAccount.getId(), ...);
// (and the trend query)
```

```java
// JournalEntryService.java:251–253
private UserAccount resolveUserAccount(String currentUserEmail) {
    return userAccountService.findByEmail(currentUserEmail)
        .orElseThrow(() -> new IllegalStateException("Authenticated user account was not found."));
}
```

The `currentUserEmail` is the authenticated principal's name from `Authentication.getName()` (`JournalController.java:43, 58, 83, 99`). There is no way for one user to read another's entries via these paths. This is already proven by data-isolation tests in `JournalFlowTests` (lines 212–249, 282–332) and `JournalTrendsFlowTests` (lines 75–123).

**Conclusion**: read-side ownership is correct and tested. The IDOR risk is exclusively on the not-yet-existing write side.

### Finding 4 — SecurityConfiguration route protection (Risk #5)

`SecurityConfiguration.java` (lines 37–53):

```java
http.authorizeHttpRequests(authorize -> authorize
    .requestMatchers(
        "/", "/index", "/login", "/register", "/error",
        "/favicon.ico", "/favicon.svg", "/v1/random",
        "/css/**", "/js/**", "/images/**"
    )
    .permitAll()
    .anyRequest()
    .authenticated()
)
```

The deny-all is `anyRequest().authenticated()` — every HTTP method and every path NOT in the explicit permit list requires authentication. This is the structural safety net. Spring MVC dispatch happens after the security filter; a controller method on a protected path never executes for an unauthenticated request.

The permit list contains no journal routes. Adding a new controller method for `/journal/xxx` without also adding it to the permit list is automatically protected — unless the developer explicitly adds it to `requestMatchers(...).permitAll()`.

**The realistic regression scenario**: a developer adds a new route to `permitAll()` believing it's public (e.g., a CSRF-exempt callback, an unauth health-check that accidentally overlaps with a journal path) or inadvertently weakens the catch-all (`anyRequest().authenticated()` → `anyRequest().permitAll()`). Either is caught immediately by the existing anonymous-redirect tests.

### Finding 5 — Existing Risk #5 coverage (already substantial)

`AuthenticationFlowTests` already has explicit `redirectedUrl("/login")` assertions for all three protected journal routes:

| Test (line) | Route tested | Assertion |
|-------------|--------------|-----------|
| `anonymousJournalRequestReturnsToJournalAfterLogin` (241) | `GET /journal` | `redirectedUrl("/login")` at line 246 |
| `anonymousHistoryRequestReturnsToHistoryAfterLogin` (306) | `GET /journal/history` | `redirectedUrl("/login")` at line 310 |
| `anonymousTrendsRequestReturnsToTrendsAfterLogin` (338) | `GET /journal/trends` | `redirectedUrl("/login")` at line 343 |
| `logoutClearsJournalAccess` (387) | `GET /journal` post-logout | `redirectedUrl("/login")` at line 407 |

These tests would fail immediately if `anyRequest().authenticated()` were removed or `permitAll()` were incorrectly extended to cover journal routes.

**Genuine gap — anonymous POST /journal**: No test asserts that an unauthenticated `POST /journal` is blocked. `POST /journal` is not in the permit list, so it IS blocked by the deny-all, but the absence of an explicit test means a future developer could mistakenly believe POSTs are exempt from auth.

**Genuine gap — deny-all coverage for novel paths**: There is no test asserting that an arbitrary path not yet in any controller (e.g., `GET /journal/admin`) redirects to login. The deny-all handles this structurally, but adding an explicit assertion would make the security boundary observable in the test suite.

**Gap in test structure**: The existing anonymous-redirect assertions are buried inside multi-step saved-request/login-flow tests. They prove the behavior but don't serve as a readable, standalone "security fence" that a future reader can immediately identify as the security regression gate.

### Finding 6 — Existing test infrastructure and patterns

Tests use `@SpringBootTest` with full context + `MockMvcBuilders.webAppContextSetup(...).apply(springSecurity()).build()`. This is the correct setup to get actual Spring Security filter chain behavior in tests; the security rules enforced in tests match production.

Authentication in test requests: `.with(user("email").roles("USER"))` from `spring-security-test`. CSRF: `.with(csrf())`.

Example from `AuthenticationFlowTests.java:376–378`:
```java
mockMvc.perform(post("/logout")
    .with(csrf())
    .with(user("ela@example.com").roles("USER")))
    .andExpect(redirectedUrl("/login?logout"));
```

Example from `JournalFlowTests.java:85–91`:
```java
mockMvc.perform(post("/journal")
    .with(user(owner.getEmail()).roles("USER"))
    .with(csrf())
    .param("content", "..."))
    .andExpect(status().is3xxRedirection())
    .andExpect(redirectedUrl("/journal"));
```

Both test classes apply `springSecurity()` and isolate data via `deleteAll()` in `@BeforeEach`. This pattern is the established baseline for Phase 3 tests.

---

## Code References

- `src/main/java/com/amadeuszx/moodlog/journal/JournalController.java:40,53,78,96` — all current endpoints (no PUT/PATCH/DELETE)
- `src/main/java/com/amadeuszx/moodlog/user/SecurityConfiguration.java:37–53` — permit list + deny-all `anyRequest().authenticated()`
- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryRepository.java:27–37` — all current queries scoped by `userAccountId`; `findById` (unscoped) inherited from `JpaRepository`
- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntry.java:32–34` — `@ManyToOne userAccount` with `nullable = false`
- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:251–253` — `resolveUserAccount` resolves authenticated email to `UserAccount`
- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:74,148,151,161,166,171` — all service read paths pass `userAccount.getId()` to scoped repo queries
- `src/test/java/com/amadeuszx/moodlog/user/AuthenticationFlowTests.java:241–370` — existing anonymous-redirect tests for all three journal routes
- `src/test/java/com/amadeuszx/moodlog/journal/JournalFlowTests.java:212–332` — data isolation tests (read-side ownership proven)
- `src/test/java/com/amadeuszx/moodlog/journal/JournalTrendsFlowTests.java:75–123` — trends data isolation

---

## Architecture Insights

**Ownership model**: Ownership is encoded at the DB layer (`user_account_id FK NOT NULL`) and enforced in the service layer by resolving the authenticated email to a `UserAccount` and passing its UUID to scoped repository queries. There is no authorization annotation (`@PreAuthorize`, method security) — ownership is structural via query scoping, not declarative.

**Why this is safe for reads but fragile for writes**: The read-path queries were custom-written with `ByUserAccountId` in the method name — accidental unscoped reads are syntactically impossible for those methods. The write path for S-04 will likely use `findById` (the natural Spring Data method for "load by PK before update/delete") which is unscoped. The difference in patterns is the IDOR trap.

**Security config pattern**: Single `SecurityFilterChain` bean, explicit permit-list + deny-all. No method-level security annotations anywhere in the codebase. New routes are protected by default unless explicitly added to `requestMatchers(...).permitAll()`. The correct risk is not "a route is added without protection" but "a developer adds a needed route to permitAll() incorrectly."

**Test infrastructure**: `@SpringBootTest` + full `springSecurity()` context for all auth/security tests. MVC slices (`@WebMvcTest`) are NOT used — the team's established pattern for security tests is full-context `SpringBootTest`. This is correct for security testing because a `@WebMvcTest` with a security mock would bypass the actual `SecurityConfiguration`.

**Wrapper command**: `.\mvnw.cmd test` (Windows). H2 in-memory with `MODE=PostgreSQL` for tests; AI is stubbed (`moodlog.ai.enabled=false`, `moodlog.ai.provider=stub`).

---

## Risk Response Guidance — Corrected

### Risk #4 (IDOR) — corrected

| Field | Original | Correction |
|-------|----------|------------|
| What would prove protection | PUT/PATCH/DELETE to entry owned by user B returns 403/404 when user A is authenticated | **Split**: (a) now — a `@Disabled` stub class documents the ownership contract and forces it into the test suite before S-04 ships; (b) S-04 change — activate stubs against real endpoints |
| Must challenge | "isAuthenticated() implies authorized to mutate this specific resource" | **Still valid** — and research confirms the specific danger: S-04 will likely use `JpaRepository.findById(UUID)` which IS unscoped, unlike the current read queries |
| Context to ground | JournalController edit/delete/override endpoints | **Corrected**: endpoints do not exist yet; the grounding IS the `findById` IDOR trap |
| Cheapest layer | MockMvc with `.with(user(...))` on a cross-user mutation attempt | **Confirmed for future**; stub now, activate when S-04 ships |
| Anti-pattern | Testing only same-user happy path | **Confirmed** — and add: "using `findById` without additional `userAccountId` scope in the S-04 service method" |

### Risk #5 (Security config regression) — corrected

| Field | Original | Correction |
|-------|----------|------------|
| What would prove protection | Anonymous GET /journal, /journal/history, /journal/trends redirect to /login | **Confirmed and partially already proven** — existing tests at AuthenticationFlowTests:246,310,343. Gaps: anonymous POST /journal + deny-all catch-all path test |
| Must challenge | "testing /journal covers sibling routes" | **Corrected**: research shows all three sibling routes ARE already individually tested. The actual gap is POST method coverage and standalone (non-bundled) assertion structure |
| Context to ground | SecurityConfiguration route list, deny-all fallback | **Confirmed**: SecurityConfiguration:37–53. Deny-all is `anyRequest().authenticated()` |
| Cheapest layer | MockMvc anonymous GET on each distinct protected route | **Confirmed** — extend to include anonymous POST /journal and one novel-path deny-all test |
| Anti-pattern | Testing only one representative protected path | **Partially mitigated** by existing tests. Remaining gap: POST /journal + novel-path deny-all |

---

## Recommended Phase 3 Plan Shape

Two sub-phases of focused work:

**Sub-phase 1 — `SecurityRouteProtectionTests` (Risk #5 regression fence)**

New dedicated test class (not mixed with auth flow tests). Five focused tests:
1. Anonymous `GET /journal` → 302 to `/login`
2. Anonymous `GET /journal/history` → 302 to `/login`
3. Anonymous `GET /journal/trends` → 302 to `/login`
4. Anonymous `POST /journal` (with CSRF) → 302 to `/login`
5. Anonymous `GET /journal/nonexistent-path` → 302 to `/login` (explicit deny-all verification)

These tests are intentionally redundant with some existing `AuthenticationFlowTests` coverage — the point is a clean, standalone, readable security fence, not eliminating redundancy.

**Sub-phase 2 — `JournalEntryOwnershipTests` stub (Risk #4 contract anchor)**

New `@Disabled` test class documenting the S-04 ownership contract. Four `@Disabled` tests with descriptive `@DisplayName`:
1. "DELETE /journal/{id} returns 404 when id belongs to a different authenticated user" — the correct safe behavior using `findByIdAndUserAccountId`
2. "PATCH /journal/{id}/mood returns 404 when id belongs to a different authenticated user"
3. "DELETE /journal/{id} succeeds and removes the entry when called by the owner"
4. "PATCH /journal/{id}/mood succeeds and updates the tag when called by the owner"

These are `@Disabled` — they fail to compile (no handler method) until S-04 ships. When S-04 is implemented, removing `@Disabled` is the activation step. They serve as a contract that prevents a naive `findById` implementation from ever passing test review.

**§6.4 cookbook update**: Document the `findByIdAndUserAccountId` pattern as the mandatory ownership check for all write endpoints in this codebase.

---

## Historical Context

- `context/foundation/test-plan.md:46–61` — Risk #4 and #5 original guidance (now corrected above)
- `context/archive/2026-06-12-testing-ai-boundary-hardening/` — Phase 1 precedent: unit + integration tests using `@MockitoBean MoodClassifier`, `@SpringBootTest`, MockMvc
- `context/archive/2026-06-12-trends-time-zone-accuracy/` — Phase 2 precedent: `@SpringBootTest` + `FixedClockTestConfiguration`, data-isolation pattern with two users in the same test
- `context/foundation/roadmap.md:91–101` — S-04 current status: `proposed`, no change folder opened

---

## Open Questions

1. **S-04 endpoint contract**: Should the S-04 stub tests use 403 (forbidden) or 404 (not found) for cross-user access? Both are defensible. 404 leaks less information (doesn't confirm the entry exists). Research recommends 404 via `findByIdAndUserAccountId` returning `Optional.empty()` — but the final decision belongs to the S-04 planning phase.

2. **`@Disabled` stub class placement**: Should the stub live in `JournalFlowTests` (inline) or a separate `JournalEntryOwnershipTests` class? Separate class is cleaner and makes the stub easier to find and activate.

3. **CSRF on anonymous POSTs**: Spring Security's CSRF filter runs before the auth filter in the chain. An anonymous POST without CSRF token returns 403 (CSRF failure) before the 302 (auth redirect). The anonymous POST test should include `.with(csrf())` to test the auth redirect specifically, not the CSRF block.
